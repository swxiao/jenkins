/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;

import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockFolder;

import com.gargoylesoftware.htmlunit.AlertHandler;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;

/**
 * @author Kohsuke Kawaguchi
 */
public class SearchTest {
  
    @Rule public JenkinsRule j = new JenkinsRule();
    
    /**
     * No exact match should result in a failure status code.
     */
    @Test
    public void testFailure() throws Exception {
        try {
            j.search("no-such-thing");
            fail("404 expected");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(404,e.getResponse().getStatusCode());
        }
    }

    /**
     * Makes sure the script doesn't execute.
     */
    @Issue("JENKINS-3415")
    @Test
    public void testXSS() throws Exception {
        try {
            WebClient wc = j.createWebClient();
            wc.setAlertHandler(new AlertHandler() {
                public void handleAlert(Page page, String message) {
                    throw new AssertionError();
                }
            });
            wc.search("<script>alert('script');</script>");
            fail("404 expected");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(404,e.getResponse().getStatusCode());
        }
    }
    
    @Test
    public void testSearchByProjectName() throws Exception {
        final String projectName = "testSearchByProjectName";
        
        j.createFreeStyleProject(projectName);
        
        Page result = j.search(projectName);
        Assert.assertNotNull(result);
        j.assertGoodStatus(result);
        
        // make sure we've fetched the testSearchByDisplayName project page
        String contents = result.getWebResponse().getContentAsString();
        Assert.assertTrue(contents.contains(String.format("<title>%s [Jenkins]</title>", projectName)));
    }

    @Test
    public void testSearchByDisplayName() throws Exception {
        final String displayName = "displayName9999999";
        
        FreeStyleProject project = j.createFreeStyleProject("testSearchByDisplayName");
        project.setDisplayName(displayName);
        
        Page result = j.search(displayName);
        Assert.assertNotNull(result);
        j.assertGoodStatus(result);
        
        // make sure we've fetched the testSearchByDisplayName project page
        String contents = result.getWebResponse().getContentAsString();
        Assert.assertTrue(contents.contains(String.format("<title>%s [Jenkins]</title>", displayName)));
    }
    
    @Test
    public void testSearch2ProjectsWithSameDisplayName() throws Exception {
        // create 2 freestyle projects with the same display name
        final String projectName1 = "projectName1";
        final String projectName2 = "projectName2";
        final String projectName3 = "projectName3";
        final String displayName = "displayNameFoo";
        final String otherDisplayName = "otherDisplayName";
        
        FreeStyleProject project1 = j.createFreeStyleProject(projectName1);
        project1.setDisplayName(displayName);
        FreeStyleProject project2 = j.createFreeStyleProject(projectName2);
        project2.setDisplayName(displayName);
        FreeStyleProject project3 = j.createFreeStyleProject(projectName3);
        project3.setDisplayName(otherDisplayName);

        // make sure that on search we get back one of the projects, it doesn't
        // matter which one as long as the one that is returned has displayName
        // as the display name
        Page result = j.search(displayName);
        Assert.assertNotNull(result);
        j.assertGoodStatus(result);

        // make sure we've fetched the testSearchByDisplayName project page
        String contents = result.getWebResponse().getContentAsString();
        Assert.assertTrue(contents.contains(String.format("<title>%s [Jenkins]</title>", displayName)));
        Assert.assertFalse(contents.contains(otherDisplayName));
    }
    
    @Test
    public void testProjectNamePrecedesDisplayName() throws Exception {
        final String project1Name = "foo";
        final String project1DisplayName = "project1DisplayName";
        final String project2Name = "project2Name";
        final String project2DisplayName = project1Name;
        final String project3Name = "project3Name";
        final String project3DisplayName = "project3DisplayName";
        
        // create 1 freestyle project with the name foo
        FreeStyleProject project1 = j.createFreeStyleProject(project1Name);
        project1.setDisplayName(project1DisplayName);
        
        // create another with the display name foo
        FreeStyleProject project2 = j.createFreeStyleProject(project2Name);
        project2.setDisplayName(project2DisplayName);

        // create a third project and make sure it's not picked up by search
        FreeStyleProject project3 = j.createFreeStyleProject(project3Name);
        project3.setDisplayName(project3DisplayName);
        
        // search for foo
        Page result = j.search(project1Name);
        Assert.assertNotNull(result);
        j.assertGoodStatus(result);
        
        // make sure we get the project with the name foo
        String contents = result.getWebResponse().getContentAsString();
        Assert.assertTrue(contents.contains(String.format("<title>%s [Jenkins]</title>", project1DisplayName)));
        // make sure projects 2 and 3 were not picked up
        Assert.assertFalse(contents.contains(project2Name));
        Assert.assertFalse(contents.contains(project3Name));
        Assert.assertFalse(contents.contains(project3DisplayName));
    }
    
    @Test
    public void testGetSuggestionsHasBothNamesAndDisplayNames() throws Exception {
        final String projectName = "project name";
        final String displayName = "display name";

        FreeStyleProject project1 = j.createFreeStyleProject(projectName);
        project1.setDisplayName(displayName);
        
        WebClient wc = j.createWebClient();
        Page result = wc.goTo("search/suggest?query=name", "application/json");
        Assert.assertNotNull(result);
        j.assertGoodStatus(result);
        
        String content = result.getWebResponse().getContentAsString();
        System.out.println(content);
        JSONObject jsonContent = (JSONObject)JSONSerializer.toJSON(content);
        Assert.assertNotNull(jsonContent);
        JSONArray jsonArray = jsonContent.getJSONArray("suggestions");
        Assert.assertNotNull(jsonArray);
        
        Assert.assertEquals(2, jsonArray.size());
        
        boolean foundProjectName = false;
        boolean foundDispayName = false;
        for(Object suggestion : jsonArray) {
            JSONObject jsonSuggestion = (JSONObject)suggestion;
            
            String name = (String)jsonSuggestion.get("name");
            if(projectName.equals(name)) {
                foundProjectName = true;
            }
            else if(displayName.equals(name)) {
                foundDispayName = true;
            }
        }
        
        Assert.assertTrue(foundProjectName);
        Assert.assertTrue(foundDispayName);
    }

    /**
     * Disable/enable status shouldn't affect the search
     */
    @Issue("JENKINS-13148")
    @Test
    public void testDisabledJobShouldBeSearchable() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("foo-bar");
        assertTrue(suggest(j.jenkins.getSearchIndex(), "foo").contains(p));

        p.disable();
        assertTrue(suggest(j.jenkins.getSearchIndex(), "foo").contains(p));
    }

    /**
     * All top-level jobs should be searchable, not just jobs in the current view.
     */
    @Issue("JENKINS-13148")
    @Test
    public void testCompletionOutsideView() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("foo-bar");
        ListView v = new ListView("empty1",j.jenkins);
        ListView w = new ListView("empty2",j.jenkins);
        j.jenkins.addView(v);
        j.jenkins.addView(w);
        j.jenkins.setPrimaryView(w);

        // new view should be empty
        assertFalse(v.contains(p));
        assertFalse(w.contains(p));
        assertFalse(j.jenkins.getPrimaryView().contains(p));

        assertTrue(suggest(j.jenkins.getSearchIndex(),"foo").contains(p));
    }
    
    @Test
    public void testSearchWithinFolders() throws Exception {
        MockFolder folder1 = j.createFolder("folder1");
        FreeStyleProject p1 = folder1.createProject(FreeStyleProject.class, "myjob");
        MockFolder folder2 = j.createFolder("folder2");
        FreeStyleProject p2 = folder2.createProject(FreeStyleProject.class, "myjob");
        List<SearchItem> suggest = suggest(j.jenkins.getSearchIndex(), "myjob");
        assertTrue(suggest.contains(p1));
        assertTrue(suggest.contains(p2));
    }

    private List<SearchItem> suggest(SearchIndex index, String term) {
        List<SearchItem> result = new ArrayList<SearchItem>();
        index.suggest(term, result);
        return result;
    }
}
