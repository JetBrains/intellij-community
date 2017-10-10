// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE.txt file.
package com.intellij.projectView;

import com.intellij.JavaTestUtil;
import com.intellij.ide.structureView.impl.java.JavaInheritedMembersNodeProvider;
import com.intellij.ide.structureView.impl.java.PublicElementsFilter;
import com.intellij.ide.structureView.impl.java.VisibilitySorter;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
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
        svc.setActionActive(JavaInheritedMembersNodeProvider.ID, true);

        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          "-Class1.java\n" +
          " -Class1\n" +
          "  getValue(): int\n" +
          "  getClass(): Class<?>\n" +
          "  hashCode(): int\n" +
          "  equals(Object): boolean\n" +
          "  clone(): Object\n" +
          "  toString(): String\n" +
          "  notify(): void\n" +
          "  notifyAll(): void\n" +
          "  wait(long): void\n" +
          "  wait(long, int): void\n" +
          "  wait(): void\n" +
          "  finalize(): void\n" +
          "  myField1: boolean\n" +
          "  myField2: boolean\n");

        svc.setActionActive(JavaInheritedMembersNodeProvider.ID, false);
        svc.setActionActive(JavaInheritedMembersNodeProvider.ID, true);
        svc.setActionActive(JavaInheritedMembersNodeProvider.ID, false);


        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          "-Class1.java\n" +
          " -Class1\n" +
          "  getValue(): int\n" +
          "  myField1: boolean\n" +
          "  myField2: boolean\n");

        svc.setActionActive(PublicElementsFilter.ID, true);

        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          "-Class1.java\n" +
          " -Class1\n" +
          "  getValue(): int\n" +
          "  myField1: boolean\n" +
          "  myField2: boolean\n");

        svc.setActionActive(PublicElementsFilter.ID, false);

        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          "-Class1.java\n" +
          " -Class1\n" +
          "  getValue(): int\n" +
          "  myField1: boolean\n" +
          "  myField2: boolean\n");
      }
    });
  }

  public void testShowClassMembers() {
    doTest(new CheckAction() {
      @Override
      public void testClassStructure(StructureViewComponent svc) {
        svc.setActionActive(JavaInheritedMembersNodeProvider.ID, true);

        JTree tree = svc.getTree();
        tree.collapseRow(2);
        PlatformTestUtil.assertTreeEqual(
          tree,
          "-Class2.java\n" +
          " -Class2\n" +
          "  +InnerClass1\n" +
          "  +InnerClass2\n" +
          "  getValue(): int\n" +
          "  getClass(): Class<?>\n" +
          "  hashCode(): int\n" +
          "  equals(Object): boolean\n" +
          "  clone(): Object\n" +
          "  toString(): String\n" +
          "  notify(): void\n" +
          "  notifyAll(): void\n" +
          "  wait(long): void\n" +
          "  wait(long, int): void\n" +
          "  wait(): void\n" +
          "  finalize(): void\n" +
          "  myField1: boolean\n" +
          "  myField2: boolean\n" +
          "  myField3: boolean\n" +
          "  myField4: boolean\n");

        svc.setActionActive(JavaInheritedMembersNodeProvider.ID, false);

        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          "-Class2.java\n" +
          " -Class2\n" +
          "  +InnerClass1\n" +
          "  +InnerClass2\n" +
          "  getValue(): int\n" +
          "  myField1: boolean\n" +
          "  myField2: boolean\n" +
          "  myField3: boolean\n" +
          "  myField4: boolean\n");
      }
    });
  }

  public void testVisibilitySorter() {
    doTest(new CheckAction() {
      @Override
      public void testClassStructure(StructureViewComponent svc) {
        svc.setActionActive(JavaInheritedMembersNodeProvider.ID, false);

        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          "-Class2.java\n" +
          " -Class2\n" +
          "  __myPrivateFiield: int\n" +
          "  _myProtectedField: int\n" +
          "  myPublicField: int\n");

        svc.setActionActive(VisibilitySorter.ID, true);

        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          "-Class2.java\n" +
          " -Class2\n" +
          "  myPublicField: int\n" +
          "  _myProtectedField: int\n" +
          "  __myPrivateFiield: int\n");
      }
    });
  }

  public void testMembersOrder() {
    doTest(new CheckAction() {
      @Override
      public void testClassStructure(StructureViewComponent svc) {
        svc.setActionActive(JavaInheritedMembersNodeProvider.ID, false);

        PlatformTestUtil.assertTreeEqual(
          svc.getTree(),
          "-Class2.java\n" +
          " -Class2\n" +
          "  Class2()\n" +
          "  af(): void\n" +
          "  zf(): void\n" +
          "  ab: int\n" +
          "  z: int\n"
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
