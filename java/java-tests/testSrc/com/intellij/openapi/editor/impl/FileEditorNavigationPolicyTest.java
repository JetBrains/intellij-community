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
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.FileEditorNavigationPolicy;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.FileEditorManagerTestCase;

/**
 * Created by kosyakov on 04.06.15.
 */
public class FileEditorNavigationPolicyTest extends FileEditorManagerTestCase {

  private PsiFile myAPsiFile;
  private PsiFile myBPsiFile;

  private VirtualFile myAFile;
  private VirtualFile myBFile;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myAPsiFile = myFixture.addFileToProject("mypackage/A.java", "package mypackage; class A {}");
    myBPsiFile = myFixture.addFileToProject("mypackage/B.java", "package mypackage; class B {}");

    myAFile = myAPsiFile.getVirtualFile();
    myBFile = myBPsiFile.getVirtualFile();

    final FileEditorNavigationPolicy extension = new FileEditorNavigationPolicy() {
      @Override
      public VirtualFile getNavigationFile(VirtualFile requestedFile) {
        if (requestedFile.equals(myAFile)) {
          return myBFile;
        }
        return null;
      }

      @Override
      public OpenFileDescriptor getNavigationDescriptor(OpenFileDescriptor requestedDescriptor) {
        if (requestedDescriptor.getFile().equals(myAFile)) {
          return new OpenFileDescriptor(requestedDescriptor.getProject(), myBFile);
        }
        return null;
      }
    };

    final ExtensionPoint<FileEditorNavigationPolicy> extensionPoint =
      Extensions.getArea(getProject()).getExtensionPoint(FileEditorNavigationPolicy.EP_NAME);
    extensionPoint.registerExtension(extension);
    Disposer.register(getProject(), new Disposable() {
      @Override
      public void dispose() {
        extensionPoint.unregisterExtension(extension);
      }
    });
  }

  public void testOpenFile() {
    assertFalse(myManager.isFileOpen(myAFile));
    assertFalse(myManager.isFileOpen(myBFile));

    myManager.openFile(myAFile, true);

    assertFalse(myManager.isFileOpen(myAFile));
    assertTrue(myManager.isFileOpen(myBFile));

    myManager.closeFile(myBFile);
    myManager.openFile(myBFile, true);

    assertFalse(myManager.isFileOpen(myAFile));
    assertTrue(myManager.isFileOpen(myBFile));
  }

  public void testOpenFile_2() {
    assertFalse(myManager.isFileOpen(myAFile));
    assertFalse(myManager.isFileOpen(myBFile));

    myManager.openFile(myAFile, true, true);

    assertFalse(myManager.isFileOpen(myAFile));
    assertTrue(myManager.isFileOpen(myBFile));

    myManager.closeFile(myBFile);
    myManager.openFile(myBFile, true, true);

    assertFalse(myManager.isFileOpen(myAFile));
    assertTrue(myManager.isFileOpen(myBFile));
  }

  public void testOpenTextEditor() {
    assertFalse(myManager.isFileOpen(myAFile));
    assertFalse(myManager.isFileOpen(myBFile));

    myManager.openTextEditor(new OpenFileDescriptor(getProject(), myAFile), true);

    assertFalse(myManager.isFileOpen(myAFile));
    assertTrue(myManager.isFileOpen(myBFile));

    myManager.closeFile(myBFile);
    myManager.openTextEditor(new OpenFileDescriptor(getProject(), myBFile), true);

    assertFalse(myManager.isFileOpen(myAFile));
    assertTrue(myManager.isFileOpen(myBFile));
  }

  public void testOpenEditor() {
    assertFalse(myManager.isFileOpen(myAFile));
    assertFalse(myManager.isFileOpen(myBFile));

    myManager.openEditor(new OpenFileDescriptor(getProject(), myAFile), true);

    assertFalse(myManager.isFileOpen(myAFile));
    assertTrue(myManager.isFileOpen(myBFile));

    myManager.closeFile(myBFile);
    myManager.openEditor(new OpenFileDescriptor(getProject(), myBFile), true);

    assertFalse(myManager.isFileOpen(myAFile));
    assertTrue(myManager.isFileOpen(myBFile));
  }

  public void testActivateFileWithPsiElement() {
    assertFalse(myManager.isFileOpen(myAFile));
    assertFalse(myManager.isFileOpen(myBFile));

    NavigationUtil.activateFileWithPsiElement(myAPsiFile);

    assertFalse(myManager.isFileOpen(myAFile));
    assertTrue(myManager.isFileOpen(myBFile));

    myManager.closeFile(myBFile);
    NavigationUtil.activateFileWithPsiElement(myBPsiFile);

    assertFalse(myManager.isFileOpen(myAFile));
    assertTrue(myManager.isFileOpen(myBFile));
  }

}
