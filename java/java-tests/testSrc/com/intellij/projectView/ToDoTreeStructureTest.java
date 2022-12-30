// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.todo.AllTodosTreeBuilder;
import com.intellij.ide.todo.CurrentFileTodosTreeBuilder;
import com.intellij.ide.todo.TodoTreeStructure;
import com.intellij.ide.todo.nodes.TodoItemNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ProjectViewTestUtil;
import com.intellij.ui.tree.TreeTestUtil;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ToDoTreeStructureTest extends BaseProjectViewTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPrintInfo = new Queryable.PrintInfo(
      new String[]{"className", "fileName", "fieldName", "methodName", "packageName"},
      new String[]{"toDoFileCount", "toDoItemCount"}
    );
  }

  public void testToDo1() {
    AtomicInteger rebuildCacheCount = new AtomicInteger(0);
    class TestTodoBuilder extends AllTodosTreeBuilder {
      TestTodoBuilder() {
        super(new Tree(), ToDoTreeStructureTest.this.myProject);
      }
      @Override
      public void rebuildCache() {
        super.rebuildCache();
      }

      @Override
      protected void rebuildCache(@NotNull Set<? extends VirtualFile> files) {
        super.rebuildCache(files);
        rebuildCacheCount.incrementAndGet();
      }
    }
    TestTodoBuilder all = new TestTodoBuilder();
    try {
      all.init();
      //second rebuild, e.g. switching scope in scope based t.o.d.o panel
      all.rebuildCache();
      
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

      Assert.assertEquals(1, rebuildCacheCount.get());

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
                                               myPrintInfo);

      checkOccurrences(all, new String[]{"Item: (62,78)", "Item: (145,162)"});
    }
    finally {
      Disposer.dispose(all);
    }
 }

  //todo kirillk
  public void testToDo() {
    AllTodosTreeBuilder all = new AllTodosTreeBuilder(new Tree(), myProject);
    try {
      all.init();
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
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
                                                 """, myPrintInfo);

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
                                                                          myProject);

    try {
      builder.init();
      builder.setFile(getSrcDirectory().findSubdirectory("package1").findFile("JavaClass.java"));
      builder.updateTree();
      ProjectViewTestUtil.assertStructureEqual(builder.getTodoTreeStructure(),
                                               """
                                                 JavaClass.java
                                                  JavaClass.java
                                                   Item: (52,68)
                                                   Item: (134,151)
                                                 """, myPrintInfo);
    }
    finally {
      Disposer.dispose(builder);
    }


    
  }

  private static void checkOccurrences(final AllTodosTreeBuilder all, final String[] strings) {
    AbstractTreeStructure allTreeStructure = all.getTodoTreeStructure();
    TodoItemNode current = all.getFirstPointerForElement(allTreeStructure.getRootElement());
    for (String string : strings) {
      assertNotNull(current);
      assertEquals(string, current.getTestPresentation());
      current = all.getNextPointer(current);
    }

    assertNull(current);

    current = all.getLastPointerForElement(allTreeStructure.getRootElement());
    for (int i = strings.length - 1; i >= 0; i--) {
      String string = strings[i];
      assertNotNull(current);
      assertEquals(string, current.getTestPresentation());
      current = all.getPreviousPointer(current);
    }
    assertNull(current);
  }

}
