/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.projectView;

import com.intellij.JavaTestUtil;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.testFramework.TestSourceBasedTestCase;

public class ProjectViewSwitchingTest extends TestSourceBasedTestCase {
  @Override
  protected String getTestPath() {
    return "projectView";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((ProjectViewImpl)ProjectView.getInstance(getProject())).setupImpl(null);
  }

  @Override
  protected void runStartupActivities() {
    FavoritesManager.getInstance(getProject()).projectOpened();
    super.runStartupActivities();
  }

  @Override
  protected void tearDown() throws Exception {
    FavoritesManager.getInstance(getProject()).projectClosed();

    super.tearDown();
  }

  public void testSelectProject() {
    ProjectView projectView = ProjectView.getInstance(getProject());
    projectView.changeView(ProjectViewPane.ID);

    assertEquals(ProjectViewPane.ID, projectView.getCurrentViewId());

    //FavoritesManager favoritesManager = FavoritesManager.getInstance(getProject());
    //favoritesManager.createNewList("xxxx");
    //
    //AbstractProjectViewPane currentPane = projectView.getCurrentProjectViewPane();
    //assertEquals(FavoritesProjectViewPane.ID, currentPane.getId());
    //assertEquals("xxxx", currentPane.getSubId());
    //
    //favoritesManager.createNewList("yyyy");
    //currentPane = projectView.getCurrentProjectViewPane();
    //assertEquals(FavoritesProjectViewPane.ID, currentPane.getId());
    //assertEquals("yyyy", currentPane.getSubId());
    //
    //favoritesManager.removeFavoritesList("xxxx");
    //currentPane = projectView.getCurrentProjectViewPane();
    //assertEquals(FavoritesProjectViewPane.ID, currentPane.getId());
    //assertEquals("yyyy", currentPane.getSubId());
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
