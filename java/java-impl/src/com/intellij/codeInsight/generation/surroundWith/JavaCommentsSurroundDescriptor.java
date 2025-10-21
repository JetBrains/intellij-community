// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.folding.CustomFoldingSurroundDescriptor;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public final class JavaCommentsSurroundDescriptor implements SurroundDescriptor {

  private static final Surrounder[] SURROUNDERS = new CustomFoldingSurroundDescriptor().getSurrounders();

  @Override
  public Surrounder @NotNull [] getSurrounders() {
    return SURROUNDERS;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }

  @Override
  public PsiElement @NotNull [] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    if (startOffset >= endOffset) return PsiElement.EMPTY_ARRAY;
    PsiElement startElement = file.findElementAt(startOffset);
    PsiElement endElement = file.findElementAt(endOffset - 1);
    if (startElement == null || endElement == null) return PsiElement.EMPTY_ARRAY;

    PsiComment startComment = PsiTreeUtil.getParentOfType(startElement, PsiComment.class, false);
    if (startComment == null) return PsiElement.EMPTY_ARRAY;
    PsiComment endComment = PsiTreeUtil.getParentOfType(endElement, PsiComment.class, false);
    if (endComment == null) return PsiElement.EMPTY_ARRAY;
    if (startComment != endComment) return PsiElement.EMPTY_ARRAY;

    return new PsiElement[] { startComment };
  }
}
