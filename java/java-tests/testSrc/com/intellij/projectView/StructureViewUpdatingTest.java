/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

  public void testJavaClassStructure() {
    final PsiClass psiClass = JavaDirectoryService.getInstance().getClasses(getPackageDirectory("com/package1"))[0];
    final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    FileEditor[] fileEditors = fileEditorManager.openFile(virtualFile, false);
    final FileEditor fileEditor = fileEditors[0];
    try {
      final StructureViewComponent structureViewComponent =
        (StructureViewComponent)fileEditor.getStructureViewBuilder().createStructureView(fileEditor, myProject);
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiClass.getContainingFile());
      structureViewComponent.setActionActive(InheritedMembersNodeProvider.ID, true);
      PlatformTestUtil.assertTreeEqual(structureViewComponent.getTree(),
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

      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() {
          final int offset = document.getLineStartOffset(5);
          document.insertString(offset, "    boolean myNewField = false;\n");
        }
      }.execute().throwException();


      PsiDocumentManager.getInstance(myProject).commitDocument(document);

      PlatformTestUtil.waitForAlarm(600);

      //TreeUtil.expand(structureViewComponent.getTree(), 3);

      PlatformTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class1.java\n" +
                                                                         " -Class1\n" + "  getValue(): int\n" +
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
                                                                         "  myNewField: boolean = false\n");

      Disposer.dispose(structureViewComponent);

    }
    finally {
      fileEditorManager.closeFile(virtualFile);
    }
  }

  public void testShowClassMembers() {
    final PsiClass psiClass = JavaDirectoryService.getInstance().getClasses(getPackageDirectory("com/package1"))[0];
    final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    FileEditor[] fileEditors = fileEditorManager.openFile(virtualFile, false);
    final FileEditor fileEditor = fileEditors[0];
    final StructureViewComponent structureViewComponent =
      (StructureViewComponent)fileEditor.getStructureViewBuilder().createStructureView(fileEditor, myProject);
    try {
      PlatformTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
                                                                         " -Class2\n" +
                                                                         "  +InnerClass1\n" +
                                                                         "  +InnerClass2\n" +
                                                                         "  getValue(): int\n" +
                                                                         "  myField1: boolean\n" +
                                                                         "  myField2: boolean\n" +
                                                                         "  myField3: boolean\n" +
                                                                         "  myField4: boolean\n");

      final PsiField innerClassField = psiClass.getInnerClasses()[0].getFields()[0];

      structureViewComponent.select(innerClassField, true);

      PlatformTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
                                                                         " -Class2\n" +
                                                                         "  -InnerClass1\n" +
                                                                         "   +InnerClass12\n" +
                                                                         "   myInnerClassField: int\n" +
                                                                         "  +InnerClass2\n" +
                                                                         "  getValue(): int\n" +
                                                                         "  myField1: boolean\n" +
                                                                         "  myField2: boolean\n" +
                                                                         "  myField3: boolean\n" +
                                                                         "  myField4: boolean\n");

      CommandProcessor.getInstance().executeCommand(myProject, () -> WriteCommandAction.runWriteCommandAction(null, () -> {
        try {
          innerClassField.delete();
        }
        catch (IncorrectOperationException e) {
          fail(e.getLocalizedMessage());
        }
      }), null, null);

      PlatformTestUtil.waitForAlarm(600);

      PlatformTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
                                                                         " -Class2\n" +
                                                                         "  -InnerClass1\n" +
                                                                         "   +InnerClass12\n" +
                                                                         "  +InnerClass2\n" +
                                                                         "  getValue(): int\n" +
                                                                         "  myField1: boolean\n" +
                                                                         "  myField2: boolean\n" +
                                                                         "  myField3: boolean\n" +
                                                                         "  myField4: boolean\n");

    }
    finally {
      Disposer.dispose(structureViewComponent);
      fileEditorManager.closeFile(virtualFile);
    }
  }

  public void testExpandElementWithExitingName() {

    final VirtualFile xmlVirtualFile = getContentRoot().findFileByRelativePath("test.xml");
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    FileEditor[] fileEditors = fileEditorManager.openFile(xmlVirtualFile, false);
    final FileEditor fileEditor = fileEditors[0];
    try {
      final StructureViewComponent structureViewComponent =
        (StructureViewComponent)fileEditor.getStructureViewBuilder().createStructureView(fileEditor, myProject);

      final JTree tree = structureViewComponent.getTree();
      PlatformTestUtil.assertTreeEqual(tree, "-test.xml\n" +
                                             " -test\n" +
                                             "  +level1\n" +
                                             "  +level1\n" +
                                             "  +level1\n" +
                                             "  +level1\n");

      tree.expandPath(tree.getPathForRow(3));

      PlatformTestUtil.waitForAlarm(600);


      PlatformTestUtil.assertTreeEqual(tree,
                                       "-test.xml\n" +
                                       " -test\n" +
                                       "  +level1\n" +
                                       "  -level1\n" +
                                       "   +level2\n" +
                                       "  +level1\n" +
                                       "  +level1\n");

      Disposer.dispose(structureViewComponent);
    }
    finally {
      fileEditorManager.closeFile(xmlVirtualFile);
    }

  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
