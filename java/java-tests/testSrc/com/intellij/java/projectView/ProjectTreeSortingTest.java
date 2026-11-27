// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView;

import com.intellij.ide.projectView.NodeSortKey;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.ProjectViewFixture;
import com.intellij.projectView.TestProjectViewPSIPane;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.tree.TreePath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.intellij.projectView.ProjectViewFixture.projectViewFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ProjectTreeSortingTest {
  private final TestFixture<ProjectViewFixture> projectViewFixture = projectViewFixture();

  private ProjectView myProjectView;
  private TestProjectViewPSIPane myPane;
  private boolean myOriginalManualOrder;
  private boolean myOriginalSortByType;
  private boolean myOriginalFoldersAlwaysOnTop;

  @BeforeEach
  void setUp() {
    ProjectViewFixture fixture = projectViewFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myPane = new TestProjectViewPSIPane(fixture.getProject(), fixture.getStructure(), 9);
      myPane.createComponent();
      Disposer.register(fixture.getStructure(), myPane);

      myProjectView = ProjectView.getInstance(fixture.getProject());
      myProjectView.addProjectPane(myPane);
      myOriginalManualOrder = myProjectView.isManualOrder(myPane.getId());
      myOriginalSortByType = myProjectView.isSortByType(myPane.getId());
      myOriginalFoldersAlwaysOnTop = myProjectView.isFoldersAlwaysOnTop(myPane.getId());

      TreeUtil.expand(myPane.getTree(), 2);
    });
  }

  @AfterEach
  void tearDown() {
    if (myProjectView == null || myPane == null) {
      return;
    }
    EdtTestUtil.runInEdtAndWait(() -> {
      myProjectView.setManualOrder(myPane.getId(), myOriginalManualOrder);
      myProjectView.setSortByType(myPane.getId(), myOriginalSortByType);
      ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(myOriginalFoldersAlwaysOnTop);
      myProjectView.removeProjectPane(myPane);
    });
    myProjectView = null;
    myPane = null;
  }

  @Test
  public void testSortByName() {
    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortByType(myPane.getId(), false));
    assertTree("""
                 -sortByName
                  a.java
                  a-a.java
                  a-b.java
                  ab.java
                  b.java
                 """);
  }

  @Test
  public void testSortByType() {
    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortByType(myPane.getId(), false));
    assertTree("""
                 -sortByType
                  a.java
                  a.txt
                  b.java
                  b.txt
                 """);

    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortByType(myPane.getId(), true));
    assertTree("""
                 -sortByType
                  a.java
                  b.java
                  a.txt
                  b.txt
                 """);
  }

  @Test
  public void testSortByTime() {
    ProjectViewFixture fixture = projectViewFixture.get();
    setModificationTime("a.java", LocalDateTime.of(2023, Month.MAY, 8, 0, 0));
    setModificationTime("a.txt", LocalDateTime.of(2023, Month.MAY, 8, 0, 1));
    setModificationTime("b.java", LocalDateTime.of(2023, Month.MAY, 7, 0, 1));
    setModificationTime("b.txt", LocalDateTime.of(2023, Month.MAY, 7, 0, 0));
    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortKey(myPane.getId(), NodeSortKey.BY_NAME));
    fixture.getStructure().setSortKey(NodeSortKey.BY_NAME);
    assertTree("""
                 -sortByTime
                  +dir1
                  +dir2
                  a.java
                  a.txt
                  b.java
                  b.txt
                 """);

    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortKey(myPane.getId(), NodeSortKey.BY_TIME_ASCENDING));
    fixture.getStructure().setSortKey(NodeSortKey.BY_TIME_ASCENDING);
    assertTree("""
                 -sortByTime
                  +dir1
                  +dir2
                  b.txt
                  b.java
                  a.java
                  a.txt
                 """);

    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortKey(myPane.getId(), NodeSortKey.BY_TIME_DESCENDING));
    fixture.getStructure().setSortKey(NodeSortKey.BY_TIME_DESCENDING);
    assertTree("""
                 -sortByTime
                  +dir1
                  +dir2
                  a.txt
                  a.java
                  b.java
                  b.txt
                 """);

    EdtTestUtil.runInEdtAndWait(() -> ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false));

    // Check that folders are still on top because they are considered to have no timestamps:

    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortKey(myPane.getId(), NodeSortKey.BY_TIME_ASCENDING));
    fixture.getStructure().setSortKey(NodeSortKey.BY_TIME_ASCENDING);
    assertTree("""
                 -sortByTime
                  +dir1
                  +dir2
                  b.txt
                  b.java
                  a.java
                  a.txt
                 """);

    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortKey(myPane.getId(), NodeSortKey.BY_TIME_DESCENDING));
    fixture.getStructure().setSortKey(NodeSortKey.BY_TIME_DESCENDING);
    assertTree("""
                 -sortByTime
                  +dir1
                  +dir2
                  a.txt
                  a.java
                  b.java
                  b.txt
                 """);
  }

  private void setModificationTime(String fileName, LocalDateTime time) {
    ProjectViewFixture fixture = projectViewFixture.get();
    try {
      Files.setLastModifiedTime(fixture.getContentRoot().toNioPath().resolve(fileName), FileTime.from(time.toInstant(ZoneOffset.UTC)));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testFoldersOnTop() {
    // first, check with 'sort by type' disabled
    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortByType(myPane.getId(), false));

    EdtTestUtil.runInEdtAndWait(() -> ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(true));
    assertTree("""
                 -foldersOnTop
                  +b.java
                  +b.txt
                  a.java
                  a.txt
                  c.java
                  c.txt
                 """);

    EdtTestUtil.runInEdtAndWait(() -> ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false));
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
    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortByType(myPane.getId(), true));

    EdtTestUtil.runInEdtAndWait(() -> ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(true));
    assertTree("""
                 -foldersOnTop
                  +b.java
                  +b.txt
                  a.java
                  c.java
                  a.txt
                  c.txt
                 """);

    EdtTestUtil.runInEdtAndWait(() -> ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false));
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

  @Test
  public void testSortByTypeBetweenFilesAndFolders() {
    EdtTestUtil.runInEdtAndWait(() -> ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false));
    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortByType(myPane.getId(), false));
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

    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortByType(myPane.getId(), true));
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

  @Test
  public void testManualOrder() {
    ProjectViewFixture fixture = projectViewFixture.get();
    MyOrderProvider provider = new MyOrderProvider(fixture.getProject());
    provider.setOrder("b_ordered.java",
                      "a_folder_ordered",
                      "b_ordered.txt",
                      "a_ordered.txt",
                      "b_folder_ordered",
                      "a_ordered.java");
    fixture.getStructure().setProviders(provider);

    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setManualOrder(myPane.getId(), true));

    EdtTestUtil.runInEdtAndWait(() -> ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(true));

    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortByType(myPane.getId(), false));
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

    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortByType(myPane.getId(), true));
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

    EdtTestUtil.runInEdtAndWait(() -> ((ProjectViewImpl)myProjectView).setFoldersAlwaysOnTop(false));

    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortByType(myPane.getId(), false));
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

    EdtTestUtil.runInEdtAndWait(() -> myProjectView.setSortByType(myPane.getId(), true));
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
    EdtTestUtil.runInEdtAndWait(() -> {
      ProjectViewFixture fixture = projectViewFixture.get();
      PlatformTestUtil.waitWhileBusy(myPane.getTree());
      TreePath path = PlatformTestUtil.waitForPromise(myPane.promisePathToElement(fixture.getContentRootDirectory()));
      PlatformTestUtil.waitWhileBusy(myPane.getTree());
      Object element = path.getLastPathComponent();
      assertNotNull(element, "Element for " + fixture.getContentRootDirectory() + " not found");
      assertEquals(expected.trim(), PlatformTestUtil.print(myPane.getTree(), path, new Queryable.PrintInfo(), false));
    });
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
            public @Unmodifiable Collection<AbstractTreeNode<?>> getChildren() {
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
