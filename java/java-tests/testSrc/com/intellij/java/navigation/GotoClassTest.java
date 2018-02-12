/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.navigation;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.HeavyFileEditorManagerTestCase;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;

/**
 * @author Dmitry Avdeev
 */
public class GotoClassTest extends HeavyFileEditorManagerTestCase {

  public void testGotoClass() {

    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("Foo.java", "public class Foo {\n" +
                                                         "}\n" +
                                                         "\n" +
                                                         "class Bar {}\n");

    VirtualFile virtualFile = file.getVirtualFile();
    assertNotNull(virtualFile);
    myManager.openFile(virtualFile, true);
    assertEquals(0, getOffset(virtualFile));
    myManager.closeAllFiles();

    PsiClass psiClass = file.getClasses()[1];
    int identifierOffset = psiClass.getNameIdentifier().getTextOffset();
    NavigationUtil.activateFileWithPsiElement(psiClass);
    assertEquals(identifierOffset, getOffset(virtualFile));

    getEditor(virtualFile).getCaretModel().moveToOffset(identifierOffset + 3); // it's still inside the class, so keep it

    myManager.closeAllFiles();
    NavigationUtil.activateFileWithPsiElement(psiClass);
    assertEquals(identifierOffset + 3, getOffset(virtualFile));

    getEditor(virtualFile).getCaretModel().moveToOffset(0);
    NavigationUtil.activateFileWithPsiElement(psiClass);
    assertEquals(identifierOffset, getOffset(virtualFile));

    myManager.closeAllFiles();
    NavigationUtil.activateFileWithPsiElement(file); // GoTo file should keep offset
    assertEquals(identifierOffset, getOffset(virtualFile));
  }

  private int getOffset(VirtualFile virtualFile) {
    Editor editor = getEditor(virtualFile);
    return editor.getCaretModel().getOffset();
  }

  private Editor getEditor(VirtualFile virtualFile) {
    FileEditor[] editors = myManager.getEditors(virtualFile);
    return ((TextEditor)editors[0]).getEditor();
  }
}
