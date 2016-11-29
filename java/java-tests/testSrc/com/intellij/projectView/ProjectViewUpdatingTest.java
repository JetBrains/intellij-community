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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.ClassesTreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.uiDesigner.projectView.FormMergerTreeStructureProvider;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral"})
public class ProjectViewUpdatingTest extends BaseProjectViewTestCase {
  public void testStandardProviders() throws Exception{
    PsiFile element = JavaDirectoryService.getInstance().getClasses(getPackageDirectory())[0].getContainingFile();
    final AbstractProjectViewPSIPane pane = myStructure.createPane();
    getProjectTreeStructure().setProviders();
    pane.select(element, element.getContainingFile().getVirtualFile(), true);
    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                                     " -PsiDirectory: standardProviders\n" +
                                                     "  -PsiDirectory: src\n" +
                                                     "   -PsiDirectory: com\n" +
                                                     "    -PsiDirectory: package1\n" +
                                                     "     Class1.java\n" +
                                                     "     Class2.java\n" +
                                                     "     Class4.java\n" +
                                                     "     Form1.form\n" +
                                                     "     Form1.java\n" +
                                                     "     Form2.form\n" +
                                                     getRootFiles() +
                                                     " +External Libraries\n"
    );
    final PsiClass[] classes = JavaDirectoryService.getInstance()
      .getPackage(getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1")).getClasses();
    sortClassesByName(classes);
    WriteCommandAction.runWriteCommandAction(null, () -> classes[0].delete());


    PlatformTestUtil.waitForAlarm(600);

    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                                     " -PsiDirectory: standardProviders\n" +
                                                     "  -PsiDirectory: src\n" +
                                                     "   -PsiDirectory: com\n" +
                                                     "    -PsiDirectory: package1\n" +
                                                     "     Class2.java\n" +
                                                     "     Class4.java\n" +
                                                     "     Form1.form\n" +
                                                     "     Form1.java\n" +
                                                     "     Form2.form\n" +
                                                     getRootFiles() +
                                                     " +External Libraries\n");

  }

  public void testUpdateProjectView() throws Exception {
    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject), new FormMergerTreeStructureProvider(myProject));

    final AbstractProjectViewPSIPane pane = myStructure.createPane();
    final JTree tree = pane.getTree();
    PlatformTestUtil.assertTreeEqual(tree, "-Project\n" +
                                           " +PsiDirectory: updateProjectView\n" +
                                           getRootFiles() +
                                           " +External Libraries\n");

    final PsiJavaFile classFile = (PsiJavaFile)getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1").findFile("Form1.java");
    final PsiClass aClass = classFile.getClasses()[0];
    final PsiFile containingFile = aClass.getContainingFile();
    pane.select(aClass, containingFile.getVirtualFile(), true);
    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                                     " -PsiDirectory: updateProjectView\n" +
                                                     "  -PsiDirectory: src\n" +
                                                     "   -PsiDirectory: com\n" +
                                                     "    -PsiDirectory: package1\n" +
                                                     "     Class1\n" +
                                                     "     +Class2.java\n" +
                                                     "     Class4.java\n" +
                                                     "     Form2.form\n" +
                                                     "     -Form:Form1\n" +
                                                     "      [Form1]\n" +
                                                     "      Form1.form\n" +
                                                     getRootFiles() +
                                                     " +External Libraries\n", true);

    CommandProcessor.getInstance().executeCommand(myProject,
                                                  () -> new RenameProcessor(myProject, aClass, "Form1_renamed", false, false).run(), null, null);

    PlatformTestUtil.waitForAlarm(600);
    PlatformTestUtil.assertTreeEqual(tree, "-Project\n" +
                                           " -PsiDirectory: updateProjectView\n" +
                                           "  -PsiDirectory: src\n" +
                                           "   -PsiDirectory: com\n" +
                                           "    -PsiDirectory: package1\n" +
                                           "     Class1\n" +
                                           "     +Class2.java\n" +
                                           "     Class4.java\n" +
                                           "     Form2.form\n" +
                                           "     -Form:Form1_renamed\n" +
                                           "      Form1.form\n" +
                                           "      [Form1_renamed]\n" +
                                           getRootFiles() +
                                           " +External Libraries\n", true);

    TreeUtil.collapseAll(pane.getTree(), -1);
    PlatformTestUtil.assertTreeEqual(tree, "-Project\n" +
                                           " +PsiDirectory: updateProjectView\n" +
                                           getRootFiles() +
                                           " +External Libraries\n");

    final PsiClass aClass2 = JavaDirectoryService.getInstance()
      .createClass(getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1"), "Class6");
    PlatformTestUtil.waitForAlarm(600);
    final PsiFile containingFile2 = aClass2.getContainingFile();
    pane.select(aClass2, containingFile2.getVirtualFile(), true);
    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                                     " -PsiDirectory: updateProjectView\n" +
                                                     "  -PsiDirectory: src\n" +
                                                     "   -PsiDirectory: com\n" +
                                                     "    -PsiDirectory: package1\n" +
                                                     "     Class1\n" +
                                                     "     +Class2.java\n" +
                                                     "     Class4.java\n" +
                                                     "     [Class6]\n" +
                                                     "     Form2.form\n" +
                                                     "     +Form:Form1_renamed\n" +
                                                     getRootFiles() +
                                                     " +External Libraries\n", true);
  }

  public void testShowClassMembers() throws Exception{

    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject), new FormMergerTreeStructureProvider(myProject));

    final AbstractProjectViewPSIPane pane = myStructure.createPane();
    final JTree tree = pane.getTree();
    PlatformTestUtil.assertTreeEqual(tree, "-Project\n" +
                                           " +PsiDirectory: showClassMembers\n" +
                                           getRootFiles() +
                                           " +External Libraries\n");

    myStructure.setShowMembers(true);

    PsiJavaFile classFile = (PsiJavaFile)getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1").findFile("Class1.java");
    PsiClass aClass = classFile.getClasses()[0];
    PsiFile containingFile = aClass.getContainingFile();
    pane.select(aClass, containingFile.getVirtualFile(), true);
    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                                     " -PsiDirectory: showClassMembers\n" +
                                                     "  -PsiDirectory: src\n" +
                                                     "   -PsiDirectory: com\n" +
                                                     "    -PsiDirectory: package1\n" +
                                                     "     -[Class1]\n" +
                                                     "      +InnerClass\n" +
                                                     "      getValue():int\n" +
                                                     "      myField1:boolean\n" +
                                                     "      myField2:boolean\n" +
                                                     "     +Class2\n" +
                                                     getRootFiles() +
                                                     " +External Libraries\n", true);


    final Document document = FileDocumentManager.getInstance().getDocument(containingFile.getVirtualFile());
    final int caretPosition = document.getText().indexOf("public class InnerClass") - 1;

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(myProject,
                                                                                                         () -> document.insertString(caretPosition, "\n"),
                                                                                                         "typing",
                                                                                                         null));


    PsiDocumentManager.getInstance(myProject).commitDocument(document);
    PlatformTestUtil.waitForAlarm(600);

    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                                     " -PsiDirectory: showClassMembers\n" +
                                                     "  -PsiDirectory: src\n" +
                                                     "   -PsiDirectory: com\n" +
                                                     "    -PsiDirectory: package1\n" +
                                                     "     -[Class1]\n" +
                                                     "      +InnerClass\n" +
                                                     "      getValue():int\n" +
                                                     "      myField1:boolean\n" +
                                                     "      myField2:boolean\n" +
                                                     "     +Class2\n" +
                                                     getRootFiles() +
                                                     " +External Libraries\n", true);

    classFile = (PsiJavaFile)getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1").findFile("Class1.java");
    aClass = classFile.getClasses()[0];
    final PsiField lastField = aClass.getFields()[1];
    pane.select(lastField, containingFile.getVirtualFile(), true);

    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                                     " -PsiDirectory: showClassMembers\n" +
                                                     "  -PsiDirectory: src\n" +
                                                     "   -PsiDirectory: com\n" +
                                                     "    -PsiDirectory: package1\n" +
                                                     "     -Class1\n" +
                                                     "      +InnerClass\n" +
                                                     "      getValue():int\n" +
                                                     "      myField1:boolean\n" +
                                                     "      [myField2:boolean]\n" +
                                                     "     +Class2\n" +
                                                     getRootFiles() +
                                                     " +External Libraries\n", true);

    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        assertEquals("myField2", lastField.getName());
        lastField.setName("_firstField");
      }
      catch (IncorrectOperationException e) {
        fail(e.getMessage());
      }
    }), null, null);

    PlatformTestUtil.waitForAlarm(600);

    PlatformTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                                     " -PsiDirectory: showClassMembers\n" +
                                                     "  -PsiDirectory: src\n" +
                                                     "   -PsiDirectory: com\n" +
                                                     "    -PsiDirectory: package1\n" +
                                                     "     -Class1\n" +
                                                     "      +InnerClass\n" +
                                                     "      getValue():int\n" +
                                                     "      [_firstField:boolean]\n" +
                                                     "      myField1:boolean\n" +
                                                     "     +Class2\n" +
                                                     getRootFiles() +
                                                     " +External Libraries\n", true);
  }

  public void testAnnoyingScrolling() throws Exception{
                                  
    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject));

    final AbstractProjectViewPSIPane pane = myStructure.createPane();
    final JTree tree = pane.getTree();

    myStructure.setShowMembers(true);

    PsiJavaFile classFile = (PsiJavaFile)getContentDirectory().findSubdirectory("src").findSubdirectory("com").findSubdirectory("package1").findFile("Class1.java");
    PsiClass aClass = classFile.getClasses()[0];
    PsiFile containingFile = aClass.getContainingFile();
    PsiDirectory directory = containingFile.getContainingDirectory();
    pane.select(aClass, containingFile.getVirtualFile(), true);
    Point viewPosition = ((JViewport)tree.getParent()).getViewPosition();
    for (int i=0;i<100;i++) {
      JavaDirectoryService.getInstance().createClass(directory, "A" + i);
    }
    PlatformTestUtil.waitForAlarm(600);
    Point viewPositionAfter = ((JViewport)tree.getParent()).getViewPosition();
    assertEquals(viewPosition, viewPositionAfter);

  }

  class NodeWrapper extends AbstractTreeNode<Object> {
    String myName;
    List<NodeWrapper> myChildren = new ArrayList<>();

    public NodeWrapper(final Project project, final String value) {
      super(project, new Object());
      myName = value;
    }

    @Override
    @NotNull
    public Collection<? extends AbstractTreeNode> getChildren() {
      return myChildren;
    }

    @Override
    protected void update(final PresentationData presentation) {
      presentation.setPresentableText(myName);
    }

    public void addChild(final NodeWrapper nodeWrapper) {
      myChildren.add(nodeWrapper);
    }

    public void setName(final String s) {
      myName = s;
    }
  }

  public void testUpdatingAfterRename() throws Exception{

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

    final AbstractProjectViewPSIPane pane = myStructure.createPane();

    final JTree tree = pane.getTree();

    pane.getTreeBuilder().setNodeDescriptorComparator((o1, o2) -> {
      if (o1 instanceof NodeWrapper && o2 instanceof NodeWrapper) {
        return ((NodeWrapper)o1).getName().compareTo(((NodeWrapper)o2).getName());
      }
      else {
        return 0;
      }
    });

    tree.expandRow(2);
    TreeUtil.selectPath(tree, tree.getPathForRow(4));

    PlatformTestUtil.assertTreeEqual(tree, "-Project\n" +
                                           " -1\n" +
                                           "  +1.1\n" +
                                           "  -1.2\n" +
                                           "   1.2.1\n" +
                                           "   [1.2.2]\n" +
                                           "   1.2.3\n" +
                                           "  +1.3\n", true);

    wr12.setName("01.2");
    wr122.setName("01.2.2");

    pane.getTreeBuilder().updateFromRoot();

    PlatformTestUtil.assertTreeEqual(tree, "-Project\n" +
                                           " -1\n" +
                                           "  -01.2\n" +
                                           "   [01.2.2]\n" +
                                           "   1.2.1\n" +
                                           "   1.2.3\n" +
                                           "  +1.1\n" +
                                           "  +1.3\n", true);


  }

  private TreeStructureProvider createWrapProvider(final NodeWrapper rootWrapper) {
    return new TreeStructureProvider() {
      @NotNull
      @Override
      public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent, @NotNull Collection<AbstractTreeNode> children, ViewSettings settings) {

        if (parent instanceof NodeWrapper) {
          return children;
        }
        List<AbstractTreeNode> result = new ArrayList<>();
        result.add(rootWrapper);
        return result;
      }

      @Override
      @Nullable
      public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
        return null;
      }
    };
  }

}
