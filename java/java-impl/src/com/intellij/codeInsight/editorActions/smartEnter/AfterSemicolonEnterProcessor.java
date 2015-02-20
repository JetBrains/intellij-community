/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.text.CharArrayUtil;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 8, 2003
 * Time: 2:35:36 PM
 * To change this template use Options | File Templates.
 */
public class AfterSemicolonEnterProcessor implements EnterProcessor {
  @Override
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    if (psiElement instanceof PsiExpressionStatement ||
        psiElement instanceof PsiDeclarationStatement ||
        psiElement instanceof PsiDoWhileStatement ||
        psiElement instanceof PsiReturnStatement ||
        psiElement instanceof PsiThrowStatement ||
        psiElement instanceof PsiBreakStatement ||
        psiElement instanceof PsiContinueStatement ||
        psiElement instanceof PsiAssertStatement ||
        psiElement instanceof PsiField ||
        psiElement instanceof PsiImportStatementBase ||
        psiElement instanceof PsiMethod && !MissingMethodBodyFixer.shouldHaveBody((PsiMethod)psiElement)) {
      int errorOffset = getErrorElementOffset(psiElement);
      int elementEndOffset = psiElement.getTextRange().getEndOffset();
      if (psiElement instanceof PsiEnumConstant) {
        final CharSequence text = editor.getDocument().getCharsSequence();
        final int commaOffset = CharArrayUtil.shiftForwardUntil(text, elementEndOffset, ",");
        if (commaOffset < text.length()) {
          elementEndOffset = commaOffset + 1;
        }
      }

      if (errorOffset >= 0 && errorOffset < elementEndOffset) {
        final CharSequence text = editor.getDocument().getCharsSequence();
        if (text.charAt(errorOffset) == ' ' && text.charAt(errorOffset + 1) == ';') {
          errorOffset++;
        }
      }

      editor.getCaretModel().moveToOffset(errorOffset >= 0 ? errorOffset : elementEndOffset);
      return isModified;
    }
    return false;
  }

  private static int getErrorElementOffset(PsiElement elt) {
    final int[] offset = { -1 };
    elt.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override public void visitErrorElement(PsiErrorElement element) {
        if (offset[0] == -1) offset[0] = element.getTextRange().getStartOffset();
      }
    });
    return offset[0];
  }
}
