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
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;

import javax.swing.*;

public class JavaTreeStructureTest extends TestSourceBasedTestCase {
  @Override
  protected String getTestPath() {
    return "structureView";
  }

  public void testJavaClassStructure() {
    dotest(new CheckAction() {
      @Override
      public void testClassStructure(StructureViewComponent structureViewComponent) {
        structureViewComponent.setActionActive(JavaInheritedMembersNodeProvider.ID, true);
        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(),
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

        structureViewComponent.setActionActive(JavaInheritedMembersNodeProvider.ID, false);
        structureViewComponent.setActionActive(JavaInheritedMembersNodeProvider.ID, true);
        structureViewComponent.setActionActive(JavaInheritedMembersNodeProvider.ID, false);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class1.java\n" +
                                                                 " -Class1\n" +
                                                                 "  getValue(): int\n" +
                                                                 "  myField1: boolean\n" +
                                                                 "  myField2: boolean\n");

        structureViewComponent.setActionActive(PublicElementsFilter.ID, true);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class1.java\n" +
                                                                 " -Class1\n" +
                                                                 "  getValue(): int\n" +
                                                                 "  myField1: boolean\n" +
                                                                 "  myField2: boolean\n");

        structureViewComponent.setActionActive(PublicElementsFilter.ID, false);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class1.java\n" +
                                                                 " -Class1\n" +
                                                                 "  getValue(): int\n" +
                                                                 "  myField1: boolean\n" +
                                                                 "  myField2: boolean\n");
      }
    });
  }

  public void testShowClassMembers() {
    dotest(new CheckAction() {
      @Override
      public void testClassStructure(StructureViewComponent structureViewComponent) {
        structureViewComponent.setActionActive(JavaInheritedMembersNodeProvider.ID, true);
        final JTree tree = structureViewComponent.getTree();
        tree.collapseRow(2);
        IdeaTestUtil.assertTreeEqual(tree, "-Class2.java\n" +
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

        structureViewComponent.setActionActive(JavaInheritedMembersNodeProvider.ID, false);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
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
    dotest(new CheckAction() {
      @Override
      public void testClassStructure(StructureViewComponent structureViewComponent) {
        structureViewComponent.setActionActive(JavaInheritedMembersNodeProvider.ID, false);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
                                                                 " -Class2\n" +
                                                                 "  __myPrivateFiield: int\n" +
                                                                 "  _myProtectedField: int\n" +
                                                                 "  myPublicField: int\n");

        structureViewComponent.setActionActive(VisibilitySorter.ID, true);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
                                                                 " -Class2\n" +
                                                                 "  myPublicField: int\n" +
                                                                 "  _myProtectedField: int\n" +
                                                                 "  __myPrivateFiield: int\n");
      }
    });
  }

  public void testMembersOrder() {
    dotest(new CheckAction() {
      @Override
      public void testClassStructure(StructureViewComponent structureViewComponent) {
        structureViewComponent.setActionActive(JavaInheritedMembersNodeProvider.ID, false);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
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
  private void dotest(CheckAction checkAction) {
    final PsiClass psiClass = JavaDirectoryService.getInstance().getClasses(getPackageDirectory("com/package1"))[0];
    final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    FileEditor[] fileEditors = fileEditorManager.openFile(virtualFile, false);
    final FileEditor fileEditor = fileEditors[0];
    try {
      final StructureViewComponent structureViewComponent =
        (StructureViewComponent)fileEditor.getStructureViewBuilder().createStructureView(fileEditor, myProject);

      checkAction.testClassStructure(structureViewComponent);
      Disposer.dispose(structureViewComponent);

    }
    finally {
      fileEditorManager.closeFile(virtualFile);
    }
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
