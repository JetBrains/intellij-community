// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

public final class JavaSurroundWithStatementRangeAdjuster implements SurroundWithRangeAdjuster {

  @Override
  public @Nullable TextRange adjustSurroundWithRange(PsiFile file, TextRange selectedRange) {
    return selectedRange;
  }

  @Override
  public @Nullable TextRange adjustSurroundWithRange(PsiFile file, TextRange selectedRange, boolean hasSelection) {
    if (!hasSelection) {
      int startOffset = selectedRange.getStartOffset();
      int endOffset = selectedRange.getEndOffset();
      if (CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset).length == 0) {
        PsiElement elementAtLineStart = findNonWhiteSpaceElement(file, startOffset);
        PsiElement statement = PsiTreeUtil.getParentOfType(elementAtLineStart, PsiStatement.class, false);
        if (statement != null && statement.getTextRange().getStartOffset() == elementAtLineStart.getTextRange().getStartOffset()) {
          return statement.getTextRange();
        }
      }
    }
    return selectedRange;
  }

  private static PsiElement findNonWhiteSpaceElement(PsiFile file, int startOffset) {
    PsiElement leaf = file.findElementAt(startOffset);
    return leaf instanceof PsiWhiteSpace ? PsiTreeUtil.skipWhitespacesForward(leaf) : leaf;
  }
}
