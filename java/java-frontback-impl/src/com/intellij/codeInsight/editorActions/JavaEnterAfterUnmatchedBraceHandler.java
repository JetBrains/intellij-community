// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.AbstractBasicJavaFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionListStatement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class JavaEnterAfterUnmatchedBraceHandler extends EnterAfterUnmatchedBraceHandler {

  private JavaEnterAfterUnmatchedBraceHandler() {
  }

  @Override
  public boolean isApplicable(@NotNull PsiFile file, int caretOffset) {
    return file instanceof AbstractBasicJavaFile;
  }

  @Override
  protected int calculateOffsetToInsertClosingBraceInsideElement(PsiElement element) {
    if (element instanceof PsiErrorElement &&
        ((PsiErrorElement)element).getErrorDescription().equals(JavaPsiBundle.message("else.without.if"))) {
      return element.getTextRange().getStartOffset();
    }
    if (element instanceof PsiExpressionListStatement) {
      final PsiExpressionList list = ((PsiExpressionListStatement)element).getExpressionList();
      final PsiExpression[] expressions = list.getExpressions();
      if (expressions.length > 1) {
        return expressions[0].getTextRange().getEndOffset();
      }
    }
    return super.calculateOffsetToInsertClosingBraceInsideElement(element);
  }
}
