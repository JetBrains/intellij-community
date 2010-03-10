package com.intellij.projectView;

import com.intellij.ide.todo.AllTodosTreeBuilder;
import com.intellij.ide.todo.CurrentFileTodosTreeBuilder;
import com.intellij.ide.todo.TodoTreeStructure;
import com.intellij.ide.todo.nodes.TodoItemNode;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class ToDoTreeStructureTest extends BaseProjectViewTestCase {

  public void testToDo1() throws Exception {
    AllTodosTreeBuilder all = new AllTodosTreeBuilder(new JTree(), new DefaultTreeModel(new DefaultMutableTreeNode()), myProject);
    all.init();

    myStructure = all.getTreeStructure();
    ((TodoTreeStructure)myStructure).setFlattenPackages(true);
    assertStructureEqual("Root\n" +
                         " Summary\n" +
                         "  PsiPackage: package1.package2 (2 items in 1 file)\n" +
                         "   PsiJavaFile:JavaClass.java\n" +
                         "    Item: (62,78)\n" +
                         "    Item: (145,162)\n", null);

    checkOccurances(all, new String[]{"Item: (62,78)", "Item: (145,162)"});
    Disposer.dispose(all);
 }

  //todo kirillk
  public void _testToDo() throws Exception {
    AllTodosTreeBuilder all = new AllTodosTreeBuilder(new JTree(), new DefaultTreeModel(new DefaultMutableTreeNode()), myProject);
    all.init();

    myStructure = all.getTreeStructure();
    assertStructureEqual("Root\n" +
                         " Summary\n" +
                         "  PsiDirectory: toDo\n" +
                         "   XmlFile:xmlFile.xml\n"+
                         "    Item: (12,16)\n" +
                         "  PsiPackage: package1 (4 items in 2 files)\n" +
                         "   PsiPackage: package2 (2 items in 1 file)\n" +
                         "    PsiJavaFile:JavaClass.java\n" +
                         "     Item: (62,78)\n" +
                         "     Item: (145,162)\n" +
                         "   PsiJavaFile:JavaClass.java\n" +
                         "    Item: (52,68)\n" +
                         "    Item: (134,151)\n" +
                         "  PsiPackage: package3 (2 items in 1 file)\n" +
                         "   PsiJavaFile:JavaClass.java\n" +
                         "    Item: (53,69)\n" +
                         "    Item: (136,153)\n", null);

    checkOccurances(all, new String[]{"Item: (12,16)","Item: (62,78)", "Item: (145,162)", "Item: (52,68)", "Item: (134,151)",
    "Item: (53,69)", "Item: (136,153)"});


    final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    final JTree currentFileTree = new JTree(treeModel);
    CurrentFileTodosTreeBuilder builder = new CurrentFileTodosTreeBuilder(currentFileTree,
                                                                          treeModel,
                                                                          myProject);

    builder.init();
    builder.setFile(getSrcDirectory().findSubdirectory("package1").findFile("JavaClass.java"));
    builder.updateFromRoot();
    myStructure = builder.getTreeStructure();
    assertStructureEqual("PsiJavaFile:JavaClass.java\n" +
                         " PsiJavaFile:JavaClass.java\n" +
                         "  Item: (52,68)\n" +
                         "  Item: (134,151)\n", null);

    TreeUtil.expandAll(currentFileTree);

    currentFileTree.getSelectionModel().setSelectionPath(currentFileTree.getPathForRow(4));

    IdeaTestUtil.assertTreeEqual(currentFileTree, "-Root\n" +
                                                  " -Summary\n" +
                                                  "  -JavaClass.java\n" +
                                                  "   Item: (52,68)\n" +
                                                  "   [Item: (134,151)]\n", true);

    IdeaTestUtil.waitForAlarm(600);

    IdeaTestUtil.assertTreeEqual(currentFileTree, "-Root\n" +
                                                  " -Summary\n" +
                                                  "  -JavaClass.java\n" +
                                                  "   Item: (52,68)\n" +
                                                  "   [Item: (134,151)]\n", true);
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
