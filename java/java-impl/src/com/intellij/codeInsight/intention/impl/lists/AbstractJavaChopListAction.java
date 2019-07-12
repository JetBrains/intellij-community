// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.lists;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

public abstract class AbstractJavaChopListAction<L extends PsiElement, E extends PsiElement> extends AbstractChopListAction<L, E> {
  @Override
  @Nullable
  PsiElement prevBreak(@NotNull PsiElement element) {
    return JavaListUtils.prevBreak(element);
  }

  @Override
  @Nullable
  PsiElement nextBreak(@NotNull PsiElement element) {
    return JavaListUtils.nextBreak(element);
  }

  @Override
  int findOffsetForBreakAfter(E element) {
    PsiJavaToken token = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(element), PsiJavaToken.class);
    if (token != null && token.getTokenType() == JavaTokenType.COMMA) return token.getTextRange().getEndOffset();
    return element.getTextRange().getEndOffset();
  }

  @Override
  protected boolean canChop(List<E> elements) {
    return !JavaListUtils.containsEolComments(elements);
  }
}
