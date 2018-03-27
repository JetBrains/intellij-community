/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.projectView;

import com.intellij.ide.projectView.*;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class ProjectTreeSortingTest extends BaseProjectViewTestCase {
  private ProjectView myProjectView;
  private AbstractProjectViewPSIPane myPane;
  private boolean myOriginalManualOrder;
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
    myOriginalManualOrder = myProjectView.isManualOrder(myPane.getId());
    myOriginalSortByType = myProjectView.isSortByType(myPane.getId());
    myOriginalFoldersAlwaysOnTop = ((ProjectViewImpl)myProjectView).isFoldersAlwaysOnTop();

    TreeUtil.expand(myPane.getTree(), 2);
  }

  @Override
  public void tearDown() throws Exception {
    myProjectView.setManualOrder(myPane.getId(), myOriginalManualOrder);
    myProjectView.setSortByType(myPane.getId(), myOriginalSortByType);
    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(myOriginalFoldersAlwaysOnTop);
    myProjectView.removeProjectPane(myPane);
    myProjectView = null;
    myPane = null;

    super.tearDown();
  }

  public void testSortByName() {
    myProjectView.setSortByType(myPane.getId(), false);
    assertTree("-sortByName\n" +
               " a.java\n" +
               " a-a.java\n" +
               " a-b.java\n" +
               " ab.java\n" +
               " b.java\n");
  }

  public void testSortByType() {
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

  public void testFoldersOnTop() {
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
               " +b.java\n" +
               " c.java\n" +
               " a.txt\n" +
               " +b.txt\n"+
               " c.txt\n");
  }

  public void testSortByTypeBetweenFilesAndFolders() {
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
               " +a.java_folder\n" +
               " +b.java_folder\n" +
               " a.txt\n" +
               " b.txt\n" +
               " +a_folder\n" +
               " +b_folder\n");
  }

  public void testManualOrder() {
    MyOrderProvider provider = new MyOrderProvider(myProject);
    provider.setOrder("b_ordered.java",
                      "a_folder_ordered",
                      "b_ordered.txt",
                      "a_ordered.txt",
                      "b_folder_ordered",
                      "a_ordered.java");
    getProjectTreeStructure().setProviders(provider);

    myProjectView.setManualOrder(myPane.getId(), true);

    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(true);

    myProjectView.setSortByType(myPane.getId(), false);
    assertTree("-manualOrder\n" +
               " b_ordered.java\n" +
               " +a_folder_ordered\n" +
               " b_ordered.txt\n" +
               " a_ordered.txt\n" +
               " +b_folder_ordered\n" +
               " a_ordered.java\n" +

               " +a_folder_unordered\n" +
               " +b_folder_unordered\n" +
               " a_unordered.java\n" +
               " a_unordered.txt\n" +
               " b_unordered.java\n" +
               " b_unordered.txt\n");

    myProjectView.setSortByType(myPane.getId(), true);
    assertTree("-manualOrder\n" +
               " b_ordered.java\n" +
               " +a_folder_ordered\n" +
               " b_ordered.txt\n" +
               " a_ordered.txt\n" +
               " +b_folder_ordered\n" +
               " a_ordered.java\n" +

               " +a_folder_unordered\n" +
               " +b_folder_unordered\n" +
               " a_unordered.java\n" +
               " b_unordered.java\n" +
               " a_unordered.txt\n" +
               " b_unordered.txt\n");

    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false);

    myProjectView.setSortByType(myPane.getId(), false);
    assertTree("-manualOrder\n" +
               " b_ordered.java\n" +
               " +a_folder_ordered\n" +
               " b_ordered.txt\n" +
               " a_ordered.txt\n" +
               " +b_folder_ordered\n" +
               " a_ordered.java\n" +

               " +a_folder_unordered\n" +
               " a_unordered.java\n" +
               " a_unordered.txt\n" +
               " +b_folder_unordered\n" +
               " b_unordered.java\n" +
               " b_unordered.txt\n");

    myProjectView.setSortByType(myPane.getId(), true);
    assertTree("-manualOrder\n" +
               " b_ordered.java\n" +
               " +a_folder_ordered\n" +
               " b_ordered.txt\n" +
               " a_ordered.txt\n" +
               " +b_folder_ordered\n" +
               " a_ordered.java\n" +

               " a_unordered.java\n" +
               " b_unordered.java\n" +
               " a_unordered.txt\n" +
               " b_unordered.txt\n" +
               " +a_folder_unordered\n" +
               " +b_folder_unordered\n");
  }

  private void assertTree(String expected) {
    TreePath path = PlatformTestUtil.waitForPromise(myPane.promisePathToElement(getContentDirectory()));
    PlatformTestUtil.waitWhileBusy(myPane.getTree());
    Object element = path.getLastPathComponent();
    assertNotNull("Element for " + getContentDirectory() + " not found", element);
    assertEquals(expected, PlatformTestUtil.print(myPane.getTree(), element, new Queryable.PrintInfo(), false));
  }

  static class MyOrderProvider implements TreeStructureProvider {
    private final Project myProject;
    private final Map<String, Integer> myOrder = new LinkedHashMap<>();

    public MyOrderProvider(Project project) {
      myProject = project;
    }

    void setOrder(String... fileNames) {
      int i = 0;
      for (String each : fileNames) {
        myOrder.put(each, i++);
      }
    }

    @NotNull
    @Override
    public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                               @NotNull Collection<AbstractTreeNode> children,
                                               ViewSettings settings) {
      ArrayList<AbstractTreeNode> result = new ArrayList<>();

      for (final AbstractTreeNode child : children) {
        ProjectViewNode treeNode = (ProjectViewNode)child;
        final Object o = treeNode.getValue();
        if (o instanceof PsiFileSystemItem) {
          final Integer order = myOrder.get(((PsiFileSystemItem)o).getVirtualFile().getName());

          treeNode = new ProjectViewNode<PsiFileSystemItem>(myProject, (PsiFileSystemItem)o, settings) {
            @Override
            @NotNull
            public Collection<AbstractTreeNode> getChildren() {
              return child.getChildren();
            }

            @Override
            public String toTestString(Queryable.PrintInfo printInfo) {
              return child.toTestString(printInfo);
            }

            @Override
            public int getWeight() {
              return ((ProjectViewNode)child).getWeight();
            }

            @Nullable
            @Override
            public Comparable getSortKey() {
              return ((ProjectViewNode)child).getSortKey();
            }

            @Nullable
            @Override
            public Comparable getManualOrderKey() {
              return order == null ? null : order;
            }

            @Nullable
            @Override
            public String getQualifiedNameSortKey() {
              return ((ProjectViewNode)child).getQualifiedNameSortKey();
            }

            @Nullable
            @Override
            public Comparable getTypeSortKey() {
              return ((ProjectViewNode)child).getTypeSortKey();
            }

            @Override
            public int getTypeSortWeight(boolean sortByType) {
              return ((ProjectViewNode)child).getTypeSortWeight(sortByType);
            }

            @Override
            public String getTestPresentation() {
              return child.getTestPresentation();
            }

            @Override
            public boolean contains(@NotNull VirtualFile file) {
              return ((ProjectViewNode)child).contains(file);
            }

            @Override
            public void update(PresentationData presentation) {
            }

            @Override
            public String toString() {
              return ((PsiFileSystemItem)o).getName();
            }
          };
        }
        result.add(treeNode);
      }
      return result;
    }
  }
}
