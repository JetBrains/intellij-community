/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.projectView;

import com.intellij.JavaTestUtil;
import com.intellij.ide.structureView.impl.java.JavaInheritedMembersNodeProvider;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.application.ApplicationManager;
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

  public void testJavaClassStructure() throws Exception {
    final PsiClass psiClass = JavaDirectoryService.getInstance().getClasses(getPackageDirectory("com/package1"))[0];
    final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    FileEditor[] fileEditors = fileEditorManager.openFile(virtualFile, false);
    final FileEditor fileEditor = fileEditors[0];
    try {
      final StructureViewComponent structureViewComponent =
        (StructureViewComponent)fileEditor.getStructureViewBuilder().createStructureView(fileEditor, myProject);
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiClass.getContainingFile());
      structureViewComponent.setActionActive(JavaInheritedMembersNodeProvider.ID, true);
      PlatformTestUtil.assertTreeEqual(structureViewComponent.getTree(),
                                       "-Class1.java\n" +
                                       " -Class1\n" +
                                       "  getValue():int\n" +
                                       "  getClass():Class<? extends Object>\n" +
                                       "  hashCode():int\n" +
                                       "  equals(Object):boolean\n" +
                                       "  clone():Object\n" +
                                       "  toString():String\n" +
                                       "  notify():void\n" +
                                       "  notifyAll():void\n" +
                                       "  wait(long):void\n" +
                                       "  wait(long, int):void\n" +
                                       "  wait():void\n" +
                                       "  finalize():void\n" +
                                       "  myField1:boolean\n" +
                                       "  myField2:boolean\n");

      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() throws Throwable {
          final int offset = document.getLineStartOffset(5);
          document.insertString(offset, "    boolean myNewField = false;\n");
        }
      }.execute().throwException();


      PsiDocumentManager.getInstance(myProject).commitDocument(document);

      PlatformTestUtil.waitForAlarm(600);

      //TreeUtil.expand(structureViewComponent.getTree(), 3);

      PlatformTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class1.java\n" +
                                                                         " -Class1\n" + "  getValue():int\n" +
                                                                         "  getClass():Class<? extends Object>\n" +
                                                                         "  hashCode():int\n" +
                                                                         "  equals(Object):boolean\n" +
                                                                         "  clone():Object\n" +
                                                                         "  toString():String\n" +
                                                                         "  notify():void\n" +
                                                                         "  notifyAll():void\n" +
                                                                         "  wait(long):void\n" +
                                                                         "  wait(long, int):void\n" +
                                                                         "  wait():void\n" +
                                                                         "  finalize():void\n" +
                                                                         "  myField1:boolean\n" +
                                                                         "  myField2:boolean\n" +
                                                                         "  myNewField:boolean = false\n");

      Disposer.dispose(structureViewComponent);

    }
    finally {
      fileEditorManager.closeFile(virtualFile);
    }
  }

  public void testShowClassMembers() throws Exception {
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
                                                                         "  getValue():int\n" +
                                                                         "  myField1:boolean\n" +
                                                                         "  myField2:boolean\n" +
                                                                         "  myField3:boolean\n" +
                                                                         "  myField4:boolean\n");

      final PsiField innerClassField = psiClass.getInnerClasses()[0].getFields()[0];

      structureViewComponent.select(innerClassField, true);

      PlatformTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
                                                                         " -Class2\n" +
                                                                         "  -InnerClass1\n" +
                                                                         "   +InnerClass12\n" +
                                                                         "   myInnerClassField:int\n" +
                                                                         "  +InnerClass2\n" +
                                                                         "  getValue():int\n" +
                                                                         "  myField1:boolean\n" +
                                                                         "  myField2:boolean\n" +
                                                                         "  myField3:boolean\n" +
                                                                         "  myField4:boolean\n");

      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        @Override
        public void run() {

          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                innerClassField.delete();
              }
              catch (IncorrectOperationException e) {
                fail(e.getLocalizedMessage());
              }
            }
          });
        }
      }, null, null);

      PlatformTestUtil.waitForAlarm(600);

      PlatformTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
                                                                         " -Class2\n" +
                                                                         "  -InnerClass1\n" +
                                                                         "   +InnerClass12\n" +
                                                                         "  +InnerClass2\n" +
                                                                         "  getValue():int\n" +
                                                                         "  myField1:boolean\n" +
                                                                         "  myField2:boolean\n" +
                                                                         "  myField3:boolean\n" +
                                                                         "  myField4:boolean\n");

    }
    finally {
      Disposer.dispose(structureViewComponent);
      fileEditorManager.closeFile(virtualFile);
    }
  }

  public void testExpandElementWithExitingName() throws InterruptedException {

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

  @Override
  protected boolean isRunInWriteAction() {
    return false;
  }
}
