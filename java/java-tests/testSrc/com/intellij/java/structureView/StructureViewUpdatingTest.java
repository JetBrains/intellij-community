// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.structureView;

import com.intellij.JavaTestUtil;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;

public class StructureViewUpdatingTest extends TestSourceBasedTestCase {

  @Override
  protected String getTestPath() {
    return "structureView";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testJavaClassStructure() {
    PsiClass psiClass = JavaDirectoryService.getInstance().getClasses(getPackageDirectory("com/package1"))[0];
    VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    FileEditor[] fileEditors = fileEditorManager.openFile(virtualFile, false);
    FileEditor fileEditor = fileEditors[0];
    StructureViewComponent svc = (StructureViewComponent)fileEditor.getStructureViewBuilder()
                                                                   .createStructureView(fileEditor, myProject);
    Disposer.register(getTestRootDisposable(), svc);
    fileEditorManager.closeFile(virtualFile);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiClass.getContainingFile());
    svc.setActionActive(InheritedMembersNodeProvider.ID, true);
    PlatformTestUtil.assertTreeEqual(
      svc.getTree(),
      """
        -Class1.java
         -Class1
          getValue(): int
          getClass(): Class<?>
          hashCode(): int
          equals(Object): boolean
          clone(): Object
          toString(): String
          notify(): void
          notifyAll(): void
          wait(long): void
          wait(long, int): void
          wait(): void
          finalize(): void
          myField1: boolean
          myField2: boolean
        """);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      int offset = document.getLineStartOffset(5);
      document.insertString(offset, "    boolean myNewField = false;\n");
    });

    PsiDocumentManager.getInstance(myProject).commitDocument(document);

    PlatformTestUtil.waitForAlarm(600);

    PlatformTestUtil.assertTreeEqual(
      svc.getTree(),
      """
        -Class1.java
         -Class1
          getValue(): int
          getClass(): Class<?>
          hashCode(): int
          equals(Object): boolean
          clone(): Object
          toString(): String
          notify(): void
          notifyAll(): void
          wait(long): void
          wait(long, int): void
          wait(): void
          finalize(): void
          myField1: boolean
          myField2: boolean
          myNewField: boolean = false
        """);
  }

  public void testShowClassMembers() {
    PsiClass psiClass = JavaDirectoryService.getInstance().getClasses(getPackageDirectory("com/package1"))[0];
    VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    FileEditor[] fileEditors = fileEditorManager.openFile(virtualFile, false);
    FileEditor fileEditor = fileEditors[0];
    StructureViewComponent svc = (StructureViewComponent)fileEditor.getStructureViewBuilder()
      .createStructureView(fileEditor, myProject);
    Disposer.register(getTestRootDisposable(), svc);
    fileEditorManager.closeFile(virtualFile);
    PlatformTestUtil.waitForPromise(svc.rebuildAndUpdate());
    PlatformTestUtil.assertTreeEqual(
      svc.getTree(),
      """
        -Class2.java
         -Class2
          +InnerClass1
          +InnerClass2
          getValue(): int
          myField1: boolean
          myField2: boolean
          myField3: boolean
          myField4: boolean
        """);

    PsiField innerClassField = psiClass.getInnerClasses()[0].getFields()[0];

    svc.select(innerClassField, true);

    PlatformTestUtil.waitWhileBusy(svc.getTree());
    PlatformTestUtil.assertTreeEqual(
      svc.getTree(),
      """
        -Class2.java
         -Class2
          -InnerClass1
           +InnerClass12
           myInnerClassField: int
          +InnerClass2
          getValue(): int
          myField1: boolean
          myField2: boolean
          myField3: boolean
          myField4: boolean
        """);

    CommandProcessor.getInstance().executeCommand(myProject, () -> WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        innerClassField.delete();
      }
      catch (IncorrectOperationException e) {
        fail(e.getLocalizedMessage());
      }
    }), null, null);

    PlatformTestUtil.waitForAlarm(1000);

    PlatformTestUtil.assertTreeEqual(
      svc.getTree(),
      """
        -Class2.java
         -Class2
          -InnerClass1
           +InnerClass12
          +InnerClass2
          getValue(): int
          myField1: boolean
          myField2: boolean
          myField3: boolean
          myField4: boolean
        """);
  }

  public void testExpandElementWithExitingName() {

    VirtualFile xmlVirtualFile = getContentRoot().findFileByRelativePath("test.xml");
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    FileEditor[] fileEditors = fileEditorManager.openFile(xmlVirtualFile, false);
    FileEditor fileEditor = fileEditors[0];
    StructureViewComponent svc = (StructureViewComponent)fileEditor.getStructureViewBuilder()
      .createStructureView(fileEditor, myProject);
    Disposer.register(getTestRootDisposable(), svc);
    fileEditorManager.closeFile(xmlVirtualFile);
    PlatformTestUtil.waitForPromise(svc.rebuildAndUpdate());

    JTree tree = svc.getTree();
    PlatformTestUtil.assertTreeEqual(
      tree,
      """
        -test.xml
         -test
          +level1
          +level1
          +level1
          +level1
        """);

    tree.expandPath(tree.getPathForRow(3));

    PlatformTestUtil.assertTreeEqual(
      tree,
      """
        -test.xml
         -test
          +level1
          -level1
           +level2
          +level1
          +level1
        """);
  }
}
