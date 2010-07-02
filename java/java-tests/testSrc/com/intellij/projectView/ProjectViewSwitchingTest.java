package com.intellij.projectView;

import com.intellij.JavaTestUtil;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesProjectViewPane;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.testFramework.TestSourceBasedTestCase;

public class ProjectViewSwitchingTest extends TestSourceBasedTestCase {
  protected String getTestPath() {
    return "projectView";
  }

  protected void setUp() throws Exception {
    super.setUp();
    ((ProjectViewImpl)ProjectView.getInstance(getProject())).setupImpl(null);
  }

  @Override
  protected void runStartupActivities() {
    FavoritesManager.getInstance(getProject()).projectOpened();
    super.runStartupActivities();
  }

  protected void tearDown() throws Exception {
    FavoritesManager.getInstance(getProject()).projectClosed();

    super.tearDown();
  }

  public void testSelectProject() {
    ProjectView projectView = ProjectView.getInstance(getProject());
    projectView.changeView(ProjectViewPane.ID);

    assertEquals(ProjectViewPane.ID, projectView.getCurrentViewId());

    FavoritesManager favoritesManager = FavoritesManager.getInstance(getProject());
    favoritesManager.createNewList("xxxx");

    AbstractProjectViewPane currentPane = projectView.getCurrentProjectViewPane();
    assertEquals(FavoritesProjectViewPane.ID, currentPane.getId());
    assertEquals("xxxx", currentPane.getSubId());

    favoritesManager.createNewList("yyyy");
    currentPane = projectView.getCurrentProjectViewPane();
    assertEquals(FavoritesProjectViewPane.ID, currentPane.getId());
    assertEquals("yyyy", currentPane.getSubId());

    favoritesManager.removeFavoritesList("xxxx");
    currentPane = projectView.getCurrentProjectViewPane();
    assertEquals(FavoritesProjectViewPane.ID, currentPane.getId());
    assertEquals("yyyy", currentPane.getSubId());
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17();
  }

}
