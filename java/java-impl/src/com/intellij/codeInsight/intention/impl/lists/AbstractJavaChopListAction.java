// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.lists;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public abstract class AbstractJavaChopListAction<L extends PsiElement, E extends PsiElement> extends AbstractChopListAction<L, E> {

  @Override
  boolean hasBreakBefore(@NotNull E element) {
    PsiElement current = element.getPrevSibling();
    while (current != null && isValidIntermediateElement(current)) {
      if (current instanceof PsiWhiteSpace && current.textContains('\n')) return true;
      current = current.getPrevSibling();
    }
    return false;
  }

  @Override
  boolean hasBreakAfter(@NotNull E element) {
    PsiElement current = element.getNextSibling();
    while (current != null && isValidIntermediateElement(current)) {
      if (current instanceof PsiWhiteSpace && current.textContains('\n')) return true;
      current = current.getNextSibling();
    }
    return false;
  }

  private static boolean isValidIntermediateElement(@NotNull PsiElement element) {
    return element instanceof PsiWhiteSpace || element instanceof PsiComment ||
           (element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.COMMA);
  }

  @Override
  int findPlaceForBreakAfter(E element) {
    PsiJavaToken token = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(element), PsiJavaToken.class);
    if (token != null && token.getTokenType() == JavaTokenType.COMMA) return token.getTextRange().getEndOffset();
    return element.getTextRange().getEndOffset();
  }
}
