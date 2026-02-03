// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public final class MethodDeclarationRangeHandler implements DeclarationRangeHandler {
  @Override
  public @NotNull TextRange getDeclarationRange(final @NotNull PsiElement container) {
    PsiMethod method = (PsiMethod)container;
    final TextRange textRange = method.getModifierList().getTextRange();
    int startOffset = textRange != null ? textRange.getStartOffset():method.getTextOffset();
    int endOffset = method.getThrowsList().getTextRange().getEndOffset();
    return new TextRange(startOffset, endOffset);
  }
}
