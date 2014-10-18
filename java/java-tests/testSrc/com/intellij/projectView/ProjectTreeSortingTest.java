/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.tree.DefaultMutableTreeNode;

public class ProjectTreeSortingTest extends BaseProjectViewTestCase {
  private ProjectView myProjectView;
  private AbstractProjectViewPSIPane myPane;
  private boolean myOriginalSortByType;
  private boolean myOriginalFoldersAlwaysOnTop;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myPane = new TestProjectViewPSIPane(myProject, myStructure, 9);
    myPane.createComponent();
    Disposer.register(myStructure, myPane);

    myProjectView = ProjectView.getInstance(myProject);
    myProjectView.addProjectPane(myPane);
    myOriginalSortByType = myProjectView.isSortByType(myPane.getId());
    myOriginalFoldersAlwaysOnTop = ((ProjectViewImpl)myProjectView).isFoldersAlwaysOnTop();

    TreeUtil.expand(myPane.getTree(), 2);
  }

  @Override
  public void tearDown() throws Exception {
    myProjectView.setSortByType(myPane.getId(), myOriginalSortByType);
    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(myOriginalFoldersAlwaysOnTop);
    myProjectView.removeProjectPane(myPane);

    super.tearDown();
  }

  public void testSortByType() throws Exception {
    myProjectView.setSortByType(myPane.getId(), false);
    assertTree("-sortByType\n" +
               " a.java\n" +
               " a.txt\n" +
               " b.java\n" +
               " b.txt\n");

    myProjectView.setSortByType(myPane.getId(), true);
    assertTree("-sortByType\n" +
               " a.java\n" +
               " b.java\n" +
               " a.txt\n" +
               " b.txt\n");
  }

  public void testFoldersOnTop() throws Exception {
    // first, check with 'sort by type' disabled 
    myProjectView.setSortByType(myPane.getId(), false);

    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(true);
    assertTree("-foldersOnTop\n" +
               " +b.java\n" +
               " +b.txt\n" +
               " a.java\n" +
               " a.txt\n" +
               " c.java\n" +
               " c.txt\n");

    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false);
    assertTree("-foldersOnTop\n" +
               " a.java\n" +
               " a.txt\n" +
               " +b.java\n" +
               " +b.txt\n" +
               " c.java\n" +
               " c.txt\n");

    // now let's check the behavior, when sortByType is enabled 
    myProjectView.setSortByType(myPane.getId(), true);

    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(true);
    assertTree("-foldersOnTop\n" +
               " +b.java\n" +
               " +b.txt\n" +
               " a.java\n" +
               " c.java\n" +
               " a.txt\n" +
               " c.txt\n");

    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false);
    assertTree("-foldersOnTop\n" +
               " a.java\n" +
               " c.java\n" +
               " a.txt\n" +
               " c.txt\n" +
               " +b.java\n" +
               " +b.txt\n");
  }

  public void testSortByTypeBetweenFilesAndFolders() throws Exception {
    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false);
    myProjectView.setSortByType(myPane.getId(), false);
    assertTree("-sortByTypeBetweenFilesAndFolders\n" +
               " a.java\n" +
               " +a.java_folder\n" +
               " a.txt\n" +
               " +a_folder\n" +
               " b.java\n" +
               " +b.java_folder\n" +
               " b.txt\n" +
               " +b_folder\n");

    myProjectView.setSortByType(myPane.getId(), true);
    assertTree("-sortByTypeBetweenFilesAndFolders\n" +
               " a.java\n" +
               " b.java\n" +
               " a.txt\n" +
               " b.txt\n" +
               " +a.java_folder\n" +
               " +a_folder\n" +
               " +b.java_folder\n" +
               " +b_folder\n");
  }

  private void assertTree(String expected) {
    DefaultMutableTreeNode element = myPane.getTreeBuilder().getNodeForElement(getContentDirectory());
    assertNotNull("Element for " + getContentDirectory() + " not found", element);
    assertEquals(expected, PlatformTestUtil.print(myPane.getTree(), element, new Queryable.PrintInfo(), false));
  }
}
