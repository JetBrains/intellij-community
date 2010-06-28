package com.intellij.projectView;

import com.intellij.ide.todo.AllTodosTreeBuilder;
import com.intellij.ide.todo.CurrentFileTodosTreeBuilder;
import com.intellij.ide.todo.TodoTreeStructure;
import com.intellij.ide.todo.nodes.TodoItemNode;
import com.intellij.openapi.ui.Queryable;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class ToDoTreeStructureTest extends BaseProjectViewTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPrintInfo = new Queryable.PrintInfo(new String[] {"className", "fileName", "fieldName", "methodName", "packageName"}, new String[] {"toDoFileCount", "toDoItemCount"});
  }

  public void testToDo1() throws Exception {
    AllTodosTreeBuilder all = new AllTodosTreeBuilder(new Tree(), new DefaultTreeModel(new DefaultMutableTreeNode()), myProject);
    all.init();

    myStructure = all.getTreeStructure();
    ((TodoTreeStructure)myStructure).setFlattenPackages(true);
    assertStructureEqual("Root\n" +
                         " Summary\n" +
                         "  package2 toDoFileCount=1,toDoItemCount=2\n" +
                         "   JavaClass.java\n" +
                         "    Item: (62,78)\n" +
                         "    Item: (145,162)\n", null);

    checkOccurances(all, new String[]{"Item: (62,78)", "Item: (145,162)"});
    Disposer.dispose(all);
 }

  //todo kirillk
  public void testToDo() throws Exception {
    AllTodosTreeBuilder all = new AllTodosTreeBuilder(new Tree(), new DefaultTreeModel(new DefaultMutableTreeNode()), myProject);
    all.init();

    myStructure = all.getTreeStructure();
    assertStructureEqual("Root\n" +
                         " Summary\n" +
                         "  toDo\n" +
                         "   xmlFile.xml\n"+
                         "    Item: (12,16)\n" +
                         "  package1 toDoFileCount=2,toDoItemCount=4\n" +
                         "   package2 toDoFileCount=1,toDoItemCount=2\n" +
                         "    JavaClass.java\n" +
                         "     Item: (62,78)\n" +
                         "     Item: (145,162)\n" +
                         "   JavaClass.java\n" +
                         "    Item: (52,68)\n" +
                         "    Item: (134,151)\n" +
                         "  package3 toDoFileCount=1,toDoItemCount=2\n" +
                         "   JavaClass.java\n" +
                         "    Item: (53,69)\n" +
                         "    Item: (136,153)\n", null);

    checkOccurances(all, new String[]{"Item: (12,16)","Item: (62,78)", "Item: (145,162)", "Item: (52,68)", "Item: (134,151)",
    "Item: (53,69)", "Item: (136,153)"});


    final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    final JTree currentFileTree = new Tree(treeModel);
    CurrentFileTodosTreeBuilder builder = new CurrentFileTodosTreeBuilder(currentFileTree,
                                                                          treeModel,
                                                                          myProject);

    builder.init();
    builder.setFile(getSrcDirectory().findSubdirectory("package1").findFile("JavaClass.java"));
    builder.updateFromRoot();
    myStructure = builder.getTreeStructure();
    assertStructureEqual("JavaClass.java\n" +
                         " JavaClass.java\n" +
                         "  Item: (52,68)\n" +
                         "  Item: (134,151)\n", null);


    Disposer.dispose(builder);
    Disposer.dispose(all);
  }

  private void checkOccurances(final AllTodosTreeBuilder all, final String[] strings) {
    TodoItemNode current = all.getFirstPointerForElement(myStructure.getRootElement());
    for (int i = 0; i < strings.length; i++) {
      String string = strings[i];
      assertNotNull(current);
      assertEquals(string,  current.getTestPresentation());
      current = all.getNextPointer(current);
    }

    assertNull(current);

    current = all.getLastPointerForElement(myStructure.getRootElement());
    for (int i = strings.length - 1; i >= 0; i--) {
      String string = strings[i];
      assertNotNull(current);
      assertEquals(string, current.getTestPresentation());
      current = all.getPreviousPointer(current);
    }
    assertNull(current);
  }

}
