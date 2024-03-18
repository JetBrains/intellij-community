// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ClassesTreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.uiDesigner.projectView.FormMergerTreeStructureProvider;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral"})
public class ProjectViewUpdatingTest extends BaseProjectViewTestCase {
  public void testStandardProviders() {
    PsiFile element = JavaDirectoryService.getInstance().getClasses(getPackageDirectory())[0].getContainingFile();
    final AbstractProjectViewPane pane = myStructure.createPane();
    getProjectTreeStructure().setProviders();
    pane.select(element, element.getContainingFile().getVirtualFile(), true);
    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), """
      -Project
       -PsiDirectory: standardProviders
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           Class1.java
           Class2.java
           Class4.java
           Form1.form
           Form1.java
           Form2.form
       +External Libraries
      """
    );
    final PsiClass[] classes = JavaDirectoryService.getInstance()
      .getPackage(getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1")).getClasses();
    sortClassesByName(classes);
    WriteCommandAction.runWriteCommandAction(null, () -> classes[0].delete());


    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), """
      -Project
       -PsiDirectory: standardProviders
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           Class2.java
           Class4.java
           Form1.form
           Form1.java
           Form2.form
       +External Libraries
      """);

  }

  public void testUpdateProjectView() {
    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject), new FormMergerTreeStructureProvider(myProject));

    final AbstractProjectViewPane pane = myStructure.createPane();
    final JTree tree = pane.getTree();
    PlatformTestUtil.assertTreeEqual(tree, """
      -Project
       +PsiDirectory: updateProjectView
       +External Libraries
      """);

    final PsiJavaFile classFile = (PsiJavaFile)getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1").findFile("Form1.java");
    final PsiClass aClass = classFile.getClasses()[0];
    final PsiFile containingFile = aClass.getContainingFile();
    pane.select(aClass, containingFile.getVirtualFile(), true);
    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), """
      -Project
       -PsiDirectory: updateProjectView
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           Class1
           +Class2.java
           Class4.java
           Form2.form
           -Form:Form1
            [Form1]
            Form1.form
       +External Libraries
      """, true);

    CommandProcessor.getInstance().executeCommand(myProject,
                                                  () -> new RenameProcessor(myProject, aClass, "Form1_renamed", false, false).run(), null, null);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(tree, """
      -Project
       -PsiDirectory: updateProjectView
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           Class1
           +Class2.java
           Class4.java
           Form2.form
           -Form:Form1_renamed
            Form1.form
            [Form1_renamed]
       +External Libraries
      """, true);

    TreeUtil.collapseAll(pane.getTree(), 0);
    PlatformTestUtil.assertTreeEqual(tree, """
      -Project
       +PsiDirectory: updateProjectView
       +External Libraries
      """);

    final PsiClass aClass2 = JavaDirectoryService.getInstance()
      .createClass(getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1"), "Class6");

    final PsiFile containingFile2 = aClass2.getContainingFile();
    pane.select(aClass2, containingFile2.getVirtualFile(), true);
    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), """
      -Project
       -PsiDirectory: updateProjectView
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           Class1
           +Class2.java
           Class4.java
           [Class6]
           Form2.form
           +Form:Form1_renamed
       +External Libraries
      """, true);
  }

  public void testShowClassMembers() {

    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject), new FormMergerTreeStructureProvider(myProject));

    final AbstractProjectViewPane pane = myStructure.createPane();
    final JTree tree = pane.getTree();
    PlatformTestUtil.assertTreeEqual(tree, """
      -Project
       +PsiDirectory: showClassMembers
       +External Libraries
      """);

    myStructure.setShowMembers(true);

    PsiJavaFile classFile = (PsiJavaFile)getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1").findFile("Class1.java");
    PsiClass aClass = classFile.getClasses()[0];
    PsiFile containingFile = aClass.getContainingFile();
    pane.select(aClass, containingFile.getVirtualFile(), true);
    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), """
      -Project
       -PsiDirectory: showClassMembers
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           -[Class1]
            +InnerClass
            getValue():int
            myField1:boolean
            myField2:boolean
           +Class2
       +External Libraries
      """, true);


    final Document document = FileDocumentManager.getInstance().getDocument(containingFile.getVirtualFile());
    final int caretPosition = document.getText().indexOf("public class InnerClass") - 1;

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(myProject,
                                                                                                         () -> document.insertString(caretPosition, "\n"),
                                                                                                         "typing",
                                                                                                         null));


    PsiDocumentManager.getInstance(myProject).commitDocument(document);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), """
      -Project
       -PsiDirectory: showClassMembers
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           -[Class1]
            +InnerClass
            getValue():int
            myField1:boolean
            myField2:boolean
           +Class2
       +External Libraries
      """, true);

    classFile = (PsiJavaFile)getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1").findFile("Class1.java");
    aClass = classFile.getClasses()[0];
    final PsiField lastField = aClass.getFields()[1];
    pane.select(lastField, containingFile.getVirtualFile(), true);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(), """
      -Project
       -PsiDirectory: showClassMembers
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           -Class1
            +InnerClass
            getValue():int
            myField1:boolean
            [myField2:boolean]
           +Class2
       +External Libraries
      """, true);

    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        assertEquals("myField2", lastField.getName());
        lastField.setName("_firstField");
      }
      catch (IncorrectOperationException e) {
        fail(e.getMessage());
      }
    }), null, null);

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    if (tree.getModel() instanceof AsyncTreeModel) {
      // TODO:SAM new model loses selection of moved node for now
      tree.setSelectionPath(PlatformTestUtil.waitForPromise(pane.promisePathToElement(lastField)));
    }
    PlatformTestUtil.assertTreeEqual(pane.getTree(), """
      -Project
       -PsiDirectory: showClassMembers
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           -Class1
            +InnerClass
            getValue():int
            [_firstField:boolean]
            myField1:boolean
           +Class2
       +External Libraries
      """, true);
  }

  public void testAnnoyingScrolling() {

    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject));

    final AbstractProjectViewPane pane = myStructure.createPane();
    final JTree tree = pane.getTree();

    myStructure.setShowMembers(true);

    PsiJavaFile classFile = (PsiJavaFile)getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1").findFile("Class1.java");
    PsiClass aClass = classFile.getClasses()[0];
    PsiFile containingFile = aClass.getContainingFile();
    PsiDirectory directory = containingFile.getContainingDirectory();
    pane.select(aClass, containingFile.getVirtualFile(), true);
    PlatformTestUtil.waitWhileBusy(tree);
    Point viewPosition = ((JViewport)tree.getParent()).getViewPosition();
    for (int i=0;i<100;i++) {
      JavaDirectoryService.getInstance().createClass(directory, "A" + i);
    }
    PlatformTestUtil.waitWhileBusy(pane.getTree());
    Point viewPositionAfter = ((JViewport)tree.getParent()).getViewPosition();
    assertEquals(viewPosition, viewPositionAfter);

  }

  static class NodeWrapper extends AbstractTreeNode<Object> {
    String myName;
    List<NodeWrapper> myChildren = new ArrayList<>();

    NodeWrapper(final Project project, final String value) {
      super(project, new Object());
      myName = value;
    }

    @Override
    @NotNull
    public Collection<? extends AbstractTreeNode<?>> getChildren() {
      return myChildren;
    }

    @Override
    protected void update(@NotNull final PresentationData presentation) {
      presentation.setPresentableText(myName);
    }

    public void addChild(final NodeWrapper nodeWrapper) {
      myChildren.add(nodeWrapper);
    }

    public void setName(final String s) {
      myName = s;
    }
  }

  public void testUpdatingAfterRename() {

    final NodeWrapper rootWrapper = new NodeWrapper(myProject, "1");

    final NodeWrapper wr11 = new NodeWrapper(myProject, "1.1");
    final NodeWrapper wr12 = new NodeWrapper(myProject, "1.2");
    final NodeWrapper wr13 = new NodeWrapper(myProject, "1.3");
    final NodeWrapper wr111 = new NodeWrapper(myProject, "1.1.1");
    final NodeWrapper wr112 = new NodeWrapper(myProject, "1.1.2");
    final NodeWrapper wr113 = new NodeWrapper(myProject, "1.1.3");
    final NodeWrapper wr121 = new NodeWrapper(myProject, "1.2.1");
    final NodeWrapper wr122 = new NodeWrapper(myProject, "1.2.2");
    final NodeWrapper wr123 = new NodeWrapper(myProject, "1.2.3");
    final NodeWrapper wr131 = new NodeWrapper(myProject, "1.3.1");
    final NodeWrapper wr132 = new NodeWrapper(myProject, "1.3.2");
    final NodeWrapper wr133 = new NodeWrapper(myProject, "1.3.3");

    rootWrapper.addChild(wr11);
    rootWrapper.addChild(wr12);
    rootWrapper.addChild(wr13);

    wr11.addChild(wr111);
    wr11.addChild(wr112);
    wr11.addChild(wr113);

    wr12.addChild(wr121);
    wr12.addChild(wr122);
    wr12.addChild(wr123);

    wr13.addChild(wr131);
    wr13.addChild(wr132);
    wr13.addChild(wr133);


    getProjectTreeStructure().setProviders(createWrapProvider(rootWrapper));

    final AbstractProjectViewPane pane = myStructure.createPane();

    final JTree tree = pane.getTree();

    pane.installComparator((o1, o2) -> {
      if (o1 instanceof NodeWrapper && o2 instanceof NodeWrapper) {
        return ((NodeWrapper)o1).getName().compareTo(((NodeWrapper)o2).getName());
      }
      else {
        return 0;
      }
    });

    PlatformTestUtil.expand(tree, 0, 2);
    TreeUtil.selectPath(tree, tree.getPathForRow(4));

    PlatformTestUtil.assertTreeEqual(tree, """
      -Project
       -1
        +1.1
        -1.2
         1.2.1
         [1.2.2]
         1.2.3
        +1.3
      """, true);

    wr12.setName("01.2");
    wr122.setName("01.2.2");

    // TODO:SAM new model loses selection of moved node for now
    TreeVisitor visitor = new TreeVisitor.ByTreePath<>(tree.getSelectionPath(), o -> o);
    PlatformTestUtil.waitForCallback(pane.updateFromRoot(false));
    tree.setSelectionPath(PlatformTestUtil.waitForPromise(TreeUtil.promiseMakeVisible(tree, visitor)));

    PlatformTestUtil.assertTreeEqual(tree, """
      -Project
       -1
        -01.2
         [01.2.2]
         1.2.1
         1.2.3
        +1.1
        +1.3
      """, true);


  }

  private TreeStructureProvider createWrapProvider(final NodeWrapper rootWrapper) {
    return new TreeStructureProvider() {
      @NotNull
      @Override
      public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<AbstractTreeNode<?>> children, ViewSettings settings) {

        if (parent instanceof NodeWrapper) {
          return children;
        }
        List<AbstractTreeNode<?>> result = new ArrayList<>();
        result.add(rootWrapper);
        return result;
      }
    };
  }

  public void testHideEmptyMiddlePackages() {
    myStructure.setProviders(new ClassesTreeStructureProvider(myProject));
    myStructure.setHideEmptyMiddlePackages(true);
    myStructure.setShowMembers(true);
    myStructure.setShowLibraryContents(false);

    PsiDirectory directory = getPackageDirectory("com/company");
    AbstractProjectViewPane pane = myStructure.createPane();
    JTree tree = pane.getTree();

    assertTreeEqual(tree, " +PsiDirectory: hideEmptyMiddlePackages\n");

    TreeUtil.promiseExpandAll(tree);

    assertTreeEqual(tree, " -PsiDirectory: hideEmptyMiddlePackages\n" +
                          "  -PsiDirectory: src\n" +
                          "   -PsiDirectory: name\n" + // com.company.name
                          "    -I\n" +
                          "     m():void\n");

    directory = createSubdirectory(directory, "a");
    // PSI listener is notified synchronously and starts modifying new tree model
    // unfortunately this approach does not work for old tree builders
    PlatformTestUtil.waitWhileBusy(tree);
    TreeUtil.promiseExpandAll(tree);

    assertTreeEqual(tree, " -PsiDirectory: hideEmptyMiddlePackages\n" +
                          "  -PsiDirectory: src\n" +
                          "   -PsiDirectory: company\n" + // com.company
                          "    PsiDirectory: a\n" +
                          "    -PsiDirectory: name\n" +
                          "     -I\n" +
                          "      m():void\n");

    directory = createSubdirectory(directory, "b");

    assertTreeEqual(tree, " -PsiDirectory: hideEmptyMiddlePackages\n" +
                          "  -PsiDirectory: src\n" +
                          "   -PsiDirectory: company\n" + // com.company
                          "    PsiDirectory: b\n" + // a.b
                          "    -PsiDirectory: name\n" +
                          "     -I\n" +
                          "      m():void\n");

    directory = createSubdirectory(directory, "z");

    assertTreeEqual(tree, " -PsiDirectory: hideEmptyMiddlePackages\n" +
                          "  -PsiDirectory: src\n" +
                          "   -PsiDirectory: company\n" + // com.company
                          "    PsiDirectory: z\n" + // a.b.z
                          "    -PsiDirectory: name\n" +
                          "     -I\n" +
                          "      m():void\n");
  }

  private static void assertTreeEqual(@NotNull JTree tree, @NotNull String expected) {
    PlatformTestUtil.waitWhileBusy(tree);
    PlatformTestUtil.assertTreeEqual(tree, "-Project\n" + expected);
  }

  private static PsiDirectory createSubdirectory(@NotNull PsiDirectory directory, @NotNull String name) {
    return compute(directory.getProject(), () -> directory.createSubdirectory(name));
  }

  private static <T> T compute(@NotNull Project project, @NotNull Computable<T> computable) {
    return WriteCommandAction.runWriteCommandAction(project, computable);
  }
}
