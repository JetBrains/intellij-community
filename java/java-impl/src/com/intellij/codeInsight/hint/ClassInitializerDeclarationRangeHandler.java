// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class ClassInitializerDeclarationRangeHandler implements DeclarationRangeHandler {
  @Override
  public @NotNull TextRange getDeclarationRange(final @NotNull PsiElement container) {
    PsiClassInitializer initializer = (PsiClassInitializer)container;
    int startOffset = initializer.getModifierList().getTextRange().getStartOffset();
    int endOffset = initializer.getBody().getTextRange().getStartOffset();
    return new TextRange(startOffset, endOffset);
  }
}
