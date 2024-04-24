// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hint;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;

public final class JavaImplementationTextSelectioner implements ImplementationTextSelectioner {
  private static final Logger LOG = Logger.getInstance(JavaImplementationTextSelectioner.class);

  @Override
  public int getTextStartOffset(final @NotNull PsiElement parent) {
      PsiElement element = parent;
      if (element instanceof PsiDocCommentOwner) {
        PsiDocComment comment = ((PsiDocCommentOwner)element).getDocComment();
        if (comment != null) {
          element = comment.getNextSibling();
          while (element instanceof PsiWhiteSpace) {
            element = element.getNextSibling();
          }
        }
      }

      if (element != null) {
        TextRange range = element.getTextRange();
        if (range != null) {
          return range.getStartOffset();
        }
        LOG.error("Range should not be null: " + element + "; " + element.getClass());
      }

    LOG.error("Element should not be null: " + parent.getText());
    return parent.getTextRange().getStartOffset();
  }

    @Override
    public int getTextEndOffset(@NotNull PsiElement element) {
      return element.getTextRange().getEndOffset();
    }
}