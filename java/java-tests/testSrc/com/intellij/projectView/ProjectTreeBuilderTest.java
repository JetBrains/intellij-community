// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.structureView.impl.java.JavaInheritedMembersNodeProvider;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProjectViewTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.concurrency.Promise;

import javax.swing.tree.TreePath;

public class ProjectTreeBuilderTest extends BaseProjectViewTestCase {

  public void testStandardProviders() {
    getProjectTreeStructure().setProviders();

    final PsiClass aClass = JavaDirectoryService.getInstance().getClasses(getPackageDirectory())[0];

    PsiFile element = aClass.getContainingFile();

    myStructure.checkNavigateFromSourceBehaviour(element, element.getVirtualFile(), myStructure.createPane());
  }

  public void testShowClassMembers() throws IncorrectOperationException {
    myStructure.setShowMembers(true);
    useStandardProviders();
    final PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(getPackageDirectory());
    sortClassesByName(classes);
    PsiClass aClass = classes[1];
    PsiClass innerClass1 = aClass.getInnerClasses()[0];
    PsiClass innerClass12 = innerClass1.getInnerClasses()[0];
    PsiClass innerClass13 = innerClass12.getInnerClasses()[0];
    PsiClass innerClass14 = innerClass13.getInnerClasses()[0];
    PsiClass innerClass15 = innerClass14.getInnerClasses()[0];

    PsiClass innerClass2 = aClass.getInnerClasses()[1];
    PsiClass innerClass21 = innerClass2.getInnerClasses()[0];
    PsiClass innerClass23 = innerClass21.getInnerClasses()[0];
    PsiClass innerClass24 = innerClass23.getInnerClasses()[0];

    PsiField innerClass1Field = innerClass14.getFields()[0];
    PsiField innerClass2Field = innerClass24.getFields()[0];

    final AbstractProjectViewPane pane = myStructure.createPane();

    myStructure.checkNavigateFromSourceBehaviour(innerClass2Field, innerClass2Field.getContainingFile().getVirtualFile(), pane);

    PlatformTestUtil.assertTreeEqual(pane.getTree(), """
      -Project
       -PsiDirectory: showClassMembers
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           +Class1
           -Class2
            +InnerClass1
            -InnerClass2
             -InnerClass22
              -InnerClass23
               -InnerClass24
                +InnerClass25
                myFieldToSelect:int
               myInnerClassField:int
              myInnerClassField:int
             myInnerClassField:int
            getValue():int
            myField1:boolean
            myField2:boolean
            myField3:boolean
            myField4:boolean
       +External Libraries
      """
    );

    assertFalse(ProjectViewTestUtil.isExpanded(innerClass15.getFields()[0], pane));
    assertFalse(ProjectViewTestUtil.isExpanded(innerClass1Field, pane));
    assertTrue(ProjectViewTestUtil.isExpanded(innerClass2Field, pane));

    VirtualFile virtualFile = aClass.getContainingFile().getVirtualFile();
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    FileEditor[] fileEditors = fileEditorManager.openFile(virtualFile, false);
    StructureViewComponent svc = (StructureViewComponent)fileEditors[0].getStructureViewBuilder()
      .createStructureView(fileEditors[0], myProject);
    Disposer.register(getTestRootDisposable(), svc);
    TreeUtil.collapseAll(svc.getTree(), -1);
    fileEditorManager.closeFile(virtualFile);

    Promise<TreePath> select = svc.select(innerClass2Field, true);
    PlatformTestUtil.waitForPromise(select);

    String expected = """
      -Class2.java
       -Class2
        +InnerClass1
        -InnerClass2
         -InnerClass22
          -InnerClass23
           -InnerClass24
            +InnerClass25
            myFieldToSelect: int
           myInnerClassField: int
          myInnerClassField: int
         myInnerClassField: int
        getValue(): int
        myField1: boolean
        myField2: boolean
        myField3: boolean
        myField4: boolean
      """;

    PlatformTestUtil.assertTreeEqual(svc.getTree(), expected);
    Disposer.dispose(svc);

    FileEditor fileEditor = fileEditors[0];
    StructureViewComponent svc2 = (StructureViewComponent)fileEditor.getStructureViewBuilder().createStructureView(fileEditor, myProject);
    Disposer.register(getTestRootDisposable(), svc2);
    svc2.setActionActive(JavaInheritedMembersNodeProvider.ID, false);
    PlatformTestUtil.waitWhileBusy(svc2.getTree());
    PlatformTestUtil.assertTreeEqual(svc2.getTree(), expected);
  }
}
