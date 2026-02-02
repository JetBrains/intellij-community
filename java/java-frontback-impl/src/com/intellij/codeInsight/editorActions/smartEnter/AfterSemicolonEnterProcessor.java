// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiBreakStatement;
import com.intellij.psi.PsiContinueStatement;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiYieldStatement;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class AfterSemicolonEnterProcessor implements EnterProcessor {

  @Override
  public boolean doEnter(@NotNull Editor editor, @NotNull PsiElement psiElement, boolean isModified) {
    if (psiElement instanceof PsiExpressionStatement ||
        psiElement instanceof PsiDeclarationStatement ||
        psiElement instanceof PsiDoWhileStatement ||
        psiElement instanceof PsiReturnStatement ||
        psiElement instanceof PsiThrowStatement ||
        psiElement instanceof PsiBreakStatement ||
        psiElement instanceof PsiContinueStatement ||
        psiElement instanceof PsiYieldStatement ||
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
    final int[] offset = {-1};
    elt.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitErrorElement(@NotNull PsiErrorElement element) {
        if (offset[0] == -1) offset[0] = element.getTextRange().getStartOffset();
      }
    });
    return offset[0];
  }
}
