// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.lists;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class JavaListUtils {
  private JavaListUtils() { }

  @Nullable
  static PsiElement prevBreak(@NotNull PsiElement element) {
    PsiElement current = element.getPrevSibling();
    while (current != null && isValidIntermediateElement(current)) {
      if (current instanceof PsiWhiteSpace && current.textContains('\n')) return current;
      current = current.getPrevSibling();
    }
    return null;
  }

  @Nullable
  static PsiElement nextBreak(@NotNull PsiElement element) {
    PsiElement current = element.getNextSibling();
    while (current != null && isValidIntermediateElement(current)) {
      if (current instanceof PsiWhiteSpace && current.textContains('\n')) return current;
      current = current.getNextSibling();
    }
    return null;
  }


  static boolean containsEolComments(@NotNull List<? extends PsiElement> elements) {
    PsiElement parent = elements.get(0).getParent();
    for(PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiComment && ((PsiComment)child).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        return true;
      }
    }
    return false;
  }

  private static boolean isValidIntermediateElement(@NotNull PsiElement element) {
    return element instanceof PsiWhiteSpace ||
           (element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.COMMA);
  }

  @Nullable
  static PsiExpressionList getCallArgumentsList(@NotNull PsiElement element) {
    PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(element, PsiExpressionList.class, false, PsiCodeBlock.class);
    if (expressionList == null) return null;
    PsiElement parent = expressionList.getParent();
    if (!(parent instanceof PsiCall)) return null;
    return expressionList;
  }
}
