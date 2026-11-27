// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.structureView;

import com.intellij.JavaTestUtil;
import com.intellij.ide.structureView.impl.java.KindSorter;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.TestSourceBasedFixture;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;

import static com.intellij.projectView.TestSourceBasedFixture.testSourceBasedFixture;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class StructureViewUpdatingTest {
  private final TestFixture<TestSourceBasedFixture> testSourceBasedFixture = testSourceBasedFixture("structureView", null, JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testJavaClassStructure() {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    PsiClass psiClass = ReadAction.computeBlocking(() -> JavaDirectoryService.getInstance().getClasses(fixture.getPackageDirectory("com/package1"))[0]);
    VirtualFile virtualFile = ReadAction.computeBlocking(() -> psiClass.getContainingFile().getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> {
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(fixture.getProject());
      FileEditor[] fileEditors = fileEditorManager.openFile(virtualFile, false);
      FileEditor fileEditor = fileEditors[0];
      StructureViewComponent svc = (StructureViewComponent)fileEditor.getStructureViewBuilder()
        .createStructureView(fileEditor, fixture.getProject());
      Disposer.register(fixture.getTestRootDisposable(), svc);
      fileEditorManager.closeFile(virtualFile);
      Document document = PsiDocumentManager.getInstance(fixture.getProject()).getDocument(psiClass.getContainingFile());
      svc.setActionActive(KindSorter.ID, true);
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

      WriteCommandAction.writeCommandAction(fixture.getProject()).run(() -> {
        int offset = document.getLineStartOffset(5);
        document.insertString(offset, "    boolean myNewField = false;\n");
      });

      PsiDocumentManager.getInstance(fixture.getProject()).commitDocument(document);

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
    });
  }

  @Test
  public void testShowClassMembers() {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    PsiClass psiClass = ReadAction.computeBlocking(() -> JavaDirectoryService.getInstance().getClasses(fixture.getPackageDirectory("com/package1"))[0]);
    VirtualFile virtualFile = ReadAction.computeBlocking(() -> psiClass.getContainingFile().getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> {
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(fixture.getProject());
      FileEditor[] fileEditors = fileEditorManager.openFile(virtualFile, false);
      FileEditor fileEditor = fileEditors[0];
      StructureViewComponent svc = (StructureViewComponent)fileEditor.getStructureViewBuilder()
        .createStructureView(fileEditor, fixture.getProject());
      svc.setActionActive(KindSorter.ID, true);
      Disposer.register(fixture.getTestRootDisposable(), svc);
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

      CommandProcessor.getInstance().executeCommand(fixture.getProject(), () -> WriteCommandAction.runWriteCommandAction(null, () -> {
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
    });
  }

  @Test
  public void testExpandElementWithExitingName() {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();

    EdtTestUtil.runInEdtAndWait(() -> {
      VirtualFile xmlVirtualFile = fixture.getContentRoot().findFileByRelativePath("test.xml");
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(fixture.getProject());
      FileEditor[] fileEditors = fileEditorManager.openFile(xmlVirtualFile, false);
      FileEditor fileEditor = fileEditors[0];
      StructureViewComponent svc = (StructureViewComponent)fileEditor.getStructureViewBuilder()
        .createStructureView(fileEditor, fixture.getProject());
      Disposer.register(fixture.getTestRootDisposable(), svc);
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
    });
  }
}
