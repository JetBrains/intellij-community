// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
class ElementsRange {
  private final PsiElement myStart;
  private final PsiElement myEnd;

  ElementsRange(@NotNull PsiElement start, @NotNull PsiElement end) {
    myStart = start;
    myEnd = end;
  }

  ElementsRange(PsiElement @NotNull [] elements) {
    myStart = elements[0];
    myEnd = elements[elements.length - 1];
  }

  public TextRange getTextRange() {
    if (myStart == myEnd) return myStart.getTextRange();
    return new TextRange(myStart.getTextRange().getStartOffset(), myEnd.getTextRange().getEndOffset());
  }

  public ElementsRange findCopyInFile(@NotNull PsiFile file) {
    PsiElement copyStart = findCopyInFile(file, myStart);
    PsiElement copyEnd = findCopyInFile(file, myEnd);

    if (copyStart != null && copyEnd != null) {
      return new ElementsRange(copyStart, copyEnd);
    }
    return null;
  }

  private static PsiElement findCopyInFile(@NotNull PsiFile file, @NotNull PsiElement element) {
    TextRange textRange = element.getTextRange();
    return CodeInsightUtil.findElementInRange(file, textRange.getStartOffset(), textRange.getEndOffset(), element.getClass());
  }
}
