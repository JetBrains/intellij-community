// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.structureView;

import com.intellij.JavaTestUtil;
import com.intellij.ide.structureView.impl.java.PublicElementsFilter;
import com.intellij.ide.structureView.impl.java.VisibilitySorter;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.TestSourceBasedFixture;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;

import static com.intellij.projectView.TestSourceBasedFixture.testSourceBasedFixture;

@TestApplication
public class JavaTreeStructureTest {
  private final TestFixture<TestSourceBasedFixture> testSourceBasedFixture = testSourceBasedFixture("structureView", null, JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testJavaClassStructure() {
    doTest(svc -> {
      svc.setActionActive(InheritedMembersNodeProvider.ID, true);

      PlatformTestUtil.assertTreeEqual(
        svc.getTree(),
        """
          -Class1.java
           -Class1
            myField1: boolean
            myField2: boolean
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
          """);

      svc.setActionActive(InheritedMembersNodeProvider.ID, false);
      svc.setActionActive(InheritedMembersNodeProvider.ID, true);
      svc.setActionActive(InheritedMembersNodeProvider.ID, false);


      PlatformTestUtil.assertTreeEqual(
        svc.getTree(),
        """
          -Class1.java
           -Class1
            myField1: boolean
            myField2: boolean
            getValue(): int
          """);

      svc.setActionActive(PublicElementsFilter.ID, true);

      PlatformTestUtil.assertTreeEqual(
        svc.getTree(),
        """
          -Class1.java
           -Class1
            myField1: boolean
            myField2: boolean
            getValue(): int
          """);

      svc.setActionActive(PublicElementsFilter.ID, false);

      PlatformTestUtil.assertTreeEqual(
        svc.getTree(),
        """
          -Class1.java
           -Class1
            myField1: boolean
            myField2: boolean
            getValue(): int
          """);
    });
  }

  @Test
  public void testShowClassMembers() {
    doTest(svc -> {
      svc.setActionActive(InheritedMembersNodeProvider.ID, true);

      JTree tree = svc.getTree();
      tree.collapseRow(2);
      PlatformTestUtil.assertTreeEqual(
        tree,
        """
          -Class2.java
           -Class2
            myField1: boolean
            myField2: boolean
            myField3: boolean
            myField4: boolean
            getValue(): int
            +InnerClass1
            +InnerClass2
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
          """);

      svc.setActionActive(InheritedMembersNodeProvider.ID, false);

      PlatformTestUtil.assertTreeEqual(
        svc.getTree(),
        """
          -Class2.java
           -Class2
            myField1: boolean
            myField2: boolean
            myField3: boolean
            myField4: boolean
            getValue(): int
            +InnerClass1
            +InnerClass2
          """);
    });
  }

  @Test
  public void testVisibilitySorter() {
    doTest(svc -> {
      svc.setActionActive(InheritedMembersNodeProvider.ID, false);

      PlatformTestUtil.assertTreeEqual(
        svc.getTree(),
        """
          -Class2.java
           -Class2
            __myPrivateFiield: int
            _myProtectedField: int
            myPublicField: int
          """);

      svc.setActionActive(VisibilitySorter.ID, true);

      PlatformTestUtil.assertTreeEqual(
        svc.getTree(),
        """
          -Class2.java
           -Class2
            myPublicField: int
            _myProtectedField: int
            __myPrivateFiield: int
          """);
    });
  }

  @Test
  public void testMembersOrder() {
    doTest(svc -> {
      svc.setActionActive(InheritedMembersNodeProvider.ID, false);

      PlatformTestUtil.assertTreeEqual(
        svc.getTree(),
        """
          -Class2.java
           -Class2
            ab: int
            z: int
            af(): void
            zf(): void
            Class2()
          """
      );
    });
  }

  interface CheckAction {
    void testClassStructure(StructureViewComponent structureViewComponent);
  }

  private void doTest(CheckAction checkAction) {
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
      PlatformTestUtil.waitForPromise(svc.rebuildAndUpdate());
      checkAction.testClassStructure(svc);
    });
  }
}
