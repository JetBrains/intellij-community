// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.projectView.*;
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
  private TestProjectViewPSIPane myPane;
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
    myOriginalFoldersAlwaysOnTop = myProjectView.isFoldersAlwaysOnTop(myPane.getId());

    TreeUtil.expand(myPane.getTree(), 2);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myProjectView.setManualOrder(myPane.getId(), myOriginalManualOrder);
      myProjectView.setSortByType(myPane.getId(), myOriginalSortByType);
      ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(myOriginalFoldersAlwaysOnTop);
      myProjectView.removeProjectPane(myPane);
      myProjectView = null;
      myPane = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testSortByName() {
    myProjectView.setSortByType(myPane.getId(), false);
    assertTree("""
                 -sortByName
                  a.java
                  a-a.java
                  a-b.java
                  ab.java
                  b.java
                 """);
  }

  public void testSortByType() {
    myProjectView.setSortByType(myPane.getId(), false);
    assertTree("""
                 -sortByType
                  a.java
                  a.txt
                  b.java
                  b.txt
                 """);

    myProjectView.setSortByType(myPane.getId(), true);
    assertTree("""
                 -sortByType
                  a.java
                  b.java
                  a.txt
                  b.txt
                 """);
  }

  public void testFoldersOnTop() {
    // first, check with 'sort by type' disabled
    myProjectView.setSortByType(myPane.getId(), false);

    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(true);
    assertTree("""
                 -foldersOnTop
                  +b.java
                  +b.txt
                  a.java
                  a.txt
                  c.java
                  c.txt
                 """);

    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false);
    assertTree("""
                 -foldersOnTop
                  a.java
                  a.txt
                  +b.java
                  +b.txt
                  c.java
                  c.txt
                 """);

    // now let's check the behavior, when sortByType is enabled
    myProjectView.setSortByType(myPane.getId(), true);

    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(true);
    assertTree("""
                 -foldersOnTop
                  +b.java
                  +b.txt
                  a.java
                  c.java
                  a.txt
                  c.txt
                 """);

    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false);
    assertTree("""
                 -foldersOnTop
                  a.java
                  +b.java
                  c.java
                  a.txt
                  +b.txt
                  c.txt
                 """);
  }

  public void testSortByTypeBetweenFilesAndFolders() {
    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false);
    myProjectView.setSortByType(myPane.getId(), false);
    assertTree("""
                 -sortByTypeBetweenFilesAndFolders
                  a.java
                  +a.java_folder
                  a.txt
                  +a_folder
                  b.java
                  +b.java_folder
                  b.txt
                  +b_folder
                 """);

    myProjectView.setSortByType(myPane.getId(), true);
    assertTree("""
                 -sortByTypeBetweenFilesAndFolders
                  a.java
                  b.java
                  +a.java_folder
                  +b.java_folder
                  a.txt
                  b.txt
                  +a_folder
                  +b_folder
                 """);
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
    assertTree("""
                 -manualOrder
                  b_ordered.java
                  +a_folder_ordered
                  b_ordered.txt
                  a_ordered.txt
                  +b_folder_ordered
                  a_ordered.java
                  +a_folder_unordered
                  +b_folder_unordered
                  a_unordered.java
                  a_unordered.txt
                  b_unordered.java
                  b_unordered.txt
                 """);

    myProjectView.setSortByType(myPane.getId(), true);
    assertTree("""
                 -manualOrder
                  b_ordered.java
                  +a_folder_ordered
                  b_ordered.txt
                  a_ordered.txt
                  +b_folder_ordered
                  a_ordered.java
                  +a_folder_unordered
                  +b_folder_unordered
                  a_unordered.java
                  b_unordered.java
                  a_unordered.txt
                  b_unordered.txt
                 """);

    ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false);

    myProjectView.setSortByType(myPane.getId(), false);
    assertTree("""
                 -manualOrder
                  b_ordered.java
                  +a_folder_ordered
                  b_ordered.txt
                  a_ordered.txt
                  +b_folder_ordered
                  a_ordered.java
                  +a_folder_unordered
                  a_unordered.java
                  a_unordered.txt
                  +b_folder_unordered
                  b_unordered.java
                  b_unordered.txt
                 """);

    myProjectView.setSortByType(myPane.getId(), true);
    assertTree("""
                 -manualOrder
                  b_ordered.java
                  +a_folder_ordered
                  b_ordered.txt
                  a_ordered.txt
                  +b_folder_ordered
                  a_ordered.java
                  a_unordered.java
                  b_unordered.java
                  a_unordered.txt
                  b_unordered.txt
                  +a_folder_unordered
                  +b_folder_unordered
                 """);
  }

  private void assertTree(String expected) {
    PlatformTestUtil.waitWhileBusy(myPane.getTree());
    TreePath path = PlatformTestUtil.waitForPromise(myPane.promisePathToElement(getContentDirectory()));
    PlatformTestUtil.waitWhileBusy(myPane.getTree());
    Object element = path.getLastPathComponent();
    assertNotNull("Element for " + getContentDirectory() + " not found", element);
    assertEquals(expected.trim(), PlatformTestUtil.print(myPane.getTree(), path, new Queryable.PrintInfo(), false));
  }

  static class MyOrderProvider implements TreeStructureProvider {
    private final Project myProject;
    private final Map<String, Integer> myOrder = new LinkedHashMap<>();

    MyOrderProvider(Project project) {
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
    public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent,
                                               @NotNull Collection<AbstractTreeNode<?>> children,
                                               ViewSettings settings) {
      ArrayList<AbstractTreeNode<?>> result = new ArrayList<>();

      for (final AbstractTreeNode child : children) {
        ProjectViewNode treeNode = (ProjectViewNode)child;
        final Object o = treeNode.getValue();
        if (o instanceof PsiFileSystemItem) {
          final Integer order = myOrder.get(((PsiFileSystemItem)o).getVirtualFile().getName());

          treeNode = new ProjectViewNode<>(myProject, (PsiFileSystemItem)o, settings) {
            @Override
            @NotNull
            public Collection<AbstractTreeNode<?>> getChildren() {
              return child.getChildren();
            }

            @Override
            public String toTestString(Queryable.PrintInfo printInfo) {
              return child.toTestString(printInfo);
            }

            @Override
            public int getWeight() {
              return child.getWeight();
            }

            @Nullable
            @Override
            public Comparable getSortKey() {
              return ((ProjectViewNode<?>)child).getSortKey();
            }

            @Nullable
            @Override
            public Comparable getManualOrderKey() {
              return order;
            }

            @Nullable
            @Override
            public String getQualifiedNameSortKey() {
              return ((ProjectViewNode<?>)child).getQualifiedNameSortKey();
            }

            @Nullable
            @Override
            public Comparable getTypeSortKey() {
              return ((ProjectViewNode<?>)child).getTypeSortKey();
            }

            @Override
            public int getTypeSortWeight(boolean sortByType) {
              return ((ProjectViewNode<?>)child).getTypeSortWeight(sortByType);
            }

            @Override
            public String getTestPresentation() {
              return child.getTestPresentation();
            }

            @Override
            public boolean contains(@NotNull VirtualFile file) {
              return ((ProjectViewNode<?>)child).contains(file);
            }

            @Override
            public void update(@NotNull PresentationData presentation) {
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
