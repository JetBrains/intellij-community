// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hint;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class DefaultImplementationTextSelectioner implements ImplementationTextSelectioner {
  private static final Logger LOG = Logger.getInstance(DefaultImplementationTextSelectioner.class);

  @Override
  public int getTextStartOffset(final @NotNull PsiElement parent) {
    final TextRange textRange = parent.getTextRange();
    LOG.assertTrue(textRange != null, parent);
    return textRange.getStartOffset();
  }

  @Override
  public int getTextEndOffset(@NotNull PsiElement element) {
    final TextRange textRange = element.getTextRange();
    LOG.assertTrue(textRange != null, element);
    return textRange.getEndOffset();
  }
}