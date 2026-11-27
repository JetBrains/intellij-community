// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.todo;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.todo.AllTodosTreeBuilder;
import com.intellij.ide.todo.CurrentFileTodosTreeBuilder;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.todo.TodoTreeStructure;
import com.intellij.ide.todo.nodes.TodoItemNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.projectView.ProjectViewFixture;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ProjectViewTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.tree.TreeTestUtil;
import com.intellij.ui.treeStructure.Tree;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.projectView.ProjectViewFixture.projectViewFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class ToDoTreeStructureTest {
  private final TestFixture<ProjectViewFixture> projectViewFixture = projectViewFixture(new Queryable.PrintInfo(
    new String[]{"className", "fileName", "fieldName", "methodName", "packageName"},
    new String[]{"toDoFileCount", "toDoItemCount"}
  ));

  @Test
  public void testToDo1() {
    ProjectViewFixture fixture = projectViewFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      AtomicInteger rebuildCacheCount = new AtomicInteger(0);
      AllTodosTreeBuilder all = new AllTodosTreeBuilder(new Tree(), fixture.getProject()) {

        @Override
        protected void onUpdateStarted() { }

        @Override
        protected void onUpdateFinished() {
          rebuildCacheCount.incrementAndGet();
        }
      };

      try {
        waitForBuiltCache(all);

        assertEquals(1, rebuildCacheCount.get());

        TodoTreeStructure structure = all.getTodoTreeStructure();
        structure.setFlattenPackages(true);
        ProjectViewTestUtil.assertStructureEqual(structure,
                                                 """
                                                   Root
                                                    Summary
                                                     package2 toDoFileCount=1,toDoItemCount=2
                                                      JavaClass.java
                                                       Item: (62,78)
                                                       Item: (145,162)
                                                   """,
                                                 fixture.getPrintInfo());

        checkOccurrences(all, new String[]{"Item: (62,78)", "Item: (145,162)"});
      }
      finally {
        Disposer.dispose(all);
      }
    });
  }

  @Test
  public void testToDoMarkdownDocComment() {
    ProjectViewFixture fixture = projectViewFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      AllTodosTreeBuilder all = new AllTodosTreeBuilder(new Tree(), fixture.getProject());
      try {
        waitForBuiltCache(all);

        AbstractTreeStructure structure = all.getTodoTreeStructure();
        ProjectViewTestUtil.assertStructureEqual(structure,
                                                 """
                                                   Root
                                                    Summary
                                                     package2 toDoFileCount=1,toDoItemCount=1
                                                      JavaClass.java
                                                       Item: (36,51)
                                                   """, fixture.getPrintInfo());

        checkOccurrences(all, new String[]{"Item: (36,51)"});
      }
      finally {
        Disposer.dispose(all);
      }
    });
  }

  //todo kirillk
  @Test
  public void testToDo() {
    ProjectViewFixture fixture = projectViewFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      AllTodosTreeBuilder all = new AllTodosTreeBuilder(new Tree(), fixture.getProject());
      try {
        waitForBuiltCache(all);

        AbstractTreeStructure structure = all.getTodoTreeStructure();
        ProjectViewTestUtil.assertStructureEqual(structure,
                                                 """
                                                   Root
                                                    Summary
                                                     toDo
                                                      xmlFile.xml
                                                       Item: (12,16)
                                                     package1 toDoFileCount=2,toDoItemCount=4
                                                      package2 toDoFileCount=1,toDoItemCount=2
                                                       JavaClass.java
                                                        Item: (62,78)
                                                        Item: (145,162)
                                                      JavaClass.java
                                                       Item: (52,68)
                                                       Item: (134,151)
                                                     package3 toDoFileCount=1,toDoItemCount=2
                                                      JavaClass.java
                                                       Item: (53,69)
                                                       Item: (136,153)
                                                   """, fixture.getPrintInfo());

        checkOccurrences(all, new String[]{"Item: (12,16)", "Item: (62,78)", "Item: (145,162)", "Item: (52,68)", "Item: (134,151)",
          "Item: (53,69)", "Item: (136,153)"});
      }
      finally {
        Disposer.dispose(all);
      }

      final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
      final JTree currentFileTree = new Tree(treeModel);
      TreeTestUtil.assertTreeUI(currentFileTree);
      CurrentFileTodosTreeBuilder builder = new CurrentFileTodosTreeBuilder(currentFileTree,
                                                                            fixture.getProject());

      try {
        builder.init();
        builder.setFile(fixture.getSrcDirectory().findSubdirectory("package1").findFile("JavaClass.java"));
        builder.updateTree();
        ProjectViewTestUtil.assertStructureEqual(builder.getTodoTreeStructure(),
                                                 """
                                                   JavaClass.java
                                                    JavaClass.java
                                                     Item: (52,68)
                                                     Item: (134,151)
                                                   """, fixture.getPrintInfo());
      }
      finally {
        Disposer.dispose(builder);
      }
    });
  }

  /// Initialize and wait for the cache associated with `builder` to be built
  private static void waitForBuiltCache(TodoTreeBuilder builder) {
    builder.init();
    CompletableFuture<?> rebuildCache = builder.rebuildCacheTestAccessor();
    while (!rebuildCache.isDone()) {
      IdeEventQueue.getInstance().flushQueue();
    }
  }

  private static void checkOccurrences(final AllTodosTreeBuilder all, final String[] strings) {
    AbstractTreeStructure allTreeStructure = all.getTodoTreeStructure();
    TodoItemNode current = (TodoItemNode)all.getFirstLeafForElement(allTreeStructure.getRootElement());
    for (String string : strings) {
      assertNotNull(current);
      assertEquals(string, current.getTestPresentation());
      current = (TodoItemNode)all.getNextLeaf(current);
    }

    assertNull(current);

    current = (TodoItemNode)all.getLastLeafForElement(allTreeStructure.getRootElement());
    for (int i = strings.length - 1; i >= 0; i--) {
      String string = strings[i];
      assertNotNull(current);
      assertEquals(string, current.getTestPresentation());
      current = (TodoItemNode)all.getPreviousLeaf(current);
    }
    assertNull(current);
  }
}
