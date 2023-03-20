// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.structureView;

import com.intellij.JavaTestUtil;
import com.intellij.ide.structureView.impl.java.PublicElementsFilter;
import com.intellij.ide.structureView.impl.java.VisibilitySorter;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;

import javax.swing.*;

public class JavaTreeStructureTest extends TestSourceBasedTestCase {
  @Override
  protected String getTestPath() {
    return "structureView";
  }

  public void testJavaClassStructure() {
    doTest(new CheckAction() {
      @Override
      public void testClassStructure(StructureViewComponent svc) {
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

        svc.setActionActive(InheritedMembersNodeProvider.ID, false);
        svc.setActionActive(InheritedMembersNodeProvider.ID, true);
        svc.setActionActive(InheritedMembersNodeProvider.ID, false);


        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          """
            -Class1.java
             -Class1
              getValue(): int
              myField1: boolean
              myField2: boolean
            """);

        svc.setActionActive(PublicElementsFilter.ID, true);

        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          """
            -Class1.java
             -Class1
              getValue(): int
              myField1: boolean
              myField2: boolean
            """);

        svc.setActionActive(PublicElementsFilter.ID, false);

        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          """
            -Class1.java
             -Class1
              getValue(): int
              myField1: boolean
              myField2: boolean
            """);
      }
    });
  }

  public void testShowClassMembers() {
    doTest(new CheckAction() {
      @Override
      public void testClassStructure(StructureViewComponent svc) {
        svc.setActionActive(InheritedMembersNodeProvider.ID, true);

        JTree tree = svc.getTree();
        tree.collapseRow(2);
        PlatformTestUtil.assertTreeEqual(
          tree,
          """
            -Class2.java
             -Class2
              +InnerClass1
              +InnerClass2
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
              myField3: boolean
              myField4: boolean
            """);

        svc.setActionActive(InheritedMembersNodeProvider.ID, false);

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
      }
    });
  }

  public void testVisibilitySorter() {
    doTest(new CheckAction() {
      @Override
      public void testClassStructure(StructureViewComponent svc) {
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
      }
    });
  }

  public void testMembersOrder() {
    doTest(new CheckAction() {
      @Override
      public void testClassStructure(StructureViewComponent svc) {
        svc.setActionActive(InheritedMembersNodeProvider.ID, false);

        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          """
            -Class2.java
             -Class2
              Class2()
              af(): void
              zf(): void
              ab: int
              z: int
            """
        );
      }
    });
  }

  interface CheckAction {
    void testClassStructure(StructureViewComponent structureViewComponent);
  }

  private void doTest(CheckAction checkAction) {
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
    checkAction.testClassStructure(svc);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
