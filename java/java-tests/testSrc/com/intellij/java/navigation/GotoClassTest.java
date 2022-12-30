// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.navigation;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.HeavyFileEditorManagerTestCase;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;

/**
 * @author Dmitry Avdeev
 */
public class GotoClassTest extends HeavyFileEditorManagerTestCase {
  public void testGotoClass() {
    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("Foo.java", """
      public class Foo {
      }

      class Bar {}
      """);

    VirtualFile virtualFile = file.getVirtualFile();
    assertNotNull(virtualFile);
    FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(getProject());
    manager.openFile(virtualFile, true);
    assertEquals(0, getOffset(virtualFile));
    manager.closeAllFiles();

    PsiClass psiClass = file.getClasses()[1];
    int identifierOffset = psiClass.getNameIdentifier().getTextOffset();
    NavigationUtil.activateFileWithPsiElement(psiClass);
    assertEquals(identifierOffset, getOffset(virtualFile));

    getEditor(virtualFile).getCaretModel().moveToOffset(identifierOffset + 3); // it's still inside the class, so keep it

    manager.closeAllFiles();
    NavigationUtil.activateFileWithPsiElement(psiClass);
    assertEquals(identifierOffset + 3, getOffset(virtualFile));

    getEditor(virtualFile).getCaretModel().moveToOffset(0);
    NavigationUtil.activateFileWithPsiElement(psiClass);
    assertEquals(identifierOffset, getOffset(virtualFile));

    manager.closeAllFiles();
    NavigationUtil.activateFileWithPsiElement(file); // GoTo file should keep offset
    assertEquals(identifierOffset, getOffset(virtualFile));
  }

  private int getOffset(VirtualFile virtualFile) {
    Editor editor = getEditor(virtualFile);
    return editor.getCaretModel().getOffset();
  }

  private Editor getEditor(VirtualFile virtualFile) {
    FileEditor[] editors = FileEditorManagerEx.getInstanceEx(getProject()).getEditors(virtualFile);
    return ((TextEditor)editors[0]).getEditor();
  }
}
