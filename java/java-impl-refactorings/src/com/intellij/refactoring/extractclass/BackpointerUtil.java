// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.extractclass;

import com.intellij.psi.*;

import java.util.function.Predicate;

public final class BackpointerUtil {
  private BackpointerUtil() {
  }

  public static boolean isBackpointerReference(PsiExpression expression, Predicate<? super PsiField> value) {
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiExpression contents = ((PsiParenthesizedExpression)expression).getExpression();
      return isBackpointerReference(contents, value);
    }
    if (!(expression instanceof PsiReferenceExpression reference)) {
      return false;
    }
    final PsiElement qualifier = reference.getQualifier();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
      return false;
    }
    return reference.resolve() instanceof PsiField field && value.test(field);
  }
}