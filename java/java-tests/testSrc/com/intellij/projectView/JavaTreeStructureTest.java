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
import com.intellij.ide.structureView.impl.java.InheritedMembersFilter;
import com.intellij.ide.structureView.impl.java.PublicElementsFilter;
import com.intellij.ide.structureView.impl.java.VisibilitySorter;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.TestSourceBasedTestCase;

import javax.swing.*;

public class JavaTreeStructureTest extends TestSourceBasedTestCase {
  protected String getTestPath() {
    return "structureView";
  }

  public void testJavaClassStructure() throws Exception {
    dotest(new CheckAction() {
      public void testClassStructure(StructureViewComponent structureViewComponent) {
        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(),
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

        structureViewComponent.setActionActive(InheritedMembersFilter.ID, true);
        structureViewComponent.setActionActive(InheritedMembersFilter.ID, false);
        structureViewComponent.setActionActive(InheritedMembersFilter.ID, true);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class1.java\n" +
                                                                 " -Class1\n" +
                                                                 "  getValue():int\n" +
                                                                 "  myField1:boolean\n" +
                                                                 "  myField2:boolean\n");

        structureViewComponent.setActionActive(PublicElementsFilter.ID, true);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class1.java\n" +
                                                                 " -Class1\n" +
                                                                 "  getValue():int\n" +
                                                                 "  myField1:boolean\n" +
                                                                 "  myField2:boolean\n");

        structureViewComponent.setActionActive(PublicElementsFilter.ID, false);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class1.java\n" +
                                                                 " -Class1\n" +
                                                                 "  getValue():int\n" +
                                                                 "  myField1:boolean\n" +
                                                                 "  myField2:boolean\n");
      }
    });
  }

  public void testShowClassMembers() throws Exception {
    dotest(new CheckAction() {
      public void testClassStructure(StructureViewComponent structureViewComponent) {
        final JTree tree = structureViewComponent.getTree();
        tree.collapseRow(2);
        IdeaTestUtil.assertTreeEqual(tree, "-Class2.java\n" +
                                           " -Class2\n" +
                                           "  +InnerClass1\n" +
                                           "  +InnerClass2\n" +
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
                                           "  myField2:boolean\n" +
                                           "  myField3:boolean\n" +
                                           "  myField4:boolean\n");

        structureViewComponent.setActionActive(InheritedMembersFilter.ID, true);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
                                                                       " -Class2\n" +
                                                                       "  +InnerClass1\n" +
                                                                       "  +InnerClass2\n" +
                                                                       "  getValue():int\n" +
                                                                       "  myField1:boolean\n" +
                                                                       "  myField2:boolean\n" +
                                                                       "  myField3:boolean\n" +
                                                                       "  myField4:boolean\n");
      }
    });
  }

  public void testVisibilitySorter() throws Exception {
    dotest(new CheckAction() {
      public void testClassStructure(StructureViewComponent structureViewComponent) {
        structureViewComponent.setActionActive(InheritedMembersFilter.ID, true);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
                                                                 " -Class2\n" +
                                                                 "  __myPrivateFiield:int\n" +
                                                                 "  _myProtectedField:int\n" +
                                                                 "  myPublicField:int\n");

        structureViewComponent.setActionActive(VisibilitySorter.ID, true);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
                                                                 " -Class2\n" +
                                                                 "  myPublicField:int\n" +
                                                                 "  _myProtectedField:int\n" +
                                                                 "  __myPrivateFiield:int\n");
      }
    });
  }

  public void testMembersOrder() throws Exception {
    dotest(new CheckAction() {
      public void testClassStructure(StructureViewComponent structureViewComponent) {
        structureViewComponent.setActionActive(InheritedMembersFilter.ID, true);

        IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(), "-Class2.java\n" +
                                                                 " -Class2\n" +
                                                                 "  Class2()\n" +
                                                                 "  af():void\n" +
                                                                 "  zf():void\n" +
                                                                 "  ab:int\n" +
                                                                 "  z:int\n"
        );
      }
    });
  }
  interface CheckAction {
    void testClassStructure(StructureViewComponent structureViewComponent);
  }
  private void dotest(CheckAction checkAction) throws Exception {
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

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17();
  }

}
