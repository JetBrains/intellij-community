// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression reference = (PsiReferenceExpression)expression;
    final PsiElement qualifier = reference.getQualifier();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
      return false;
    }
    final PsiElement referent = reference.resolve();
    return referent instanceof PsiField && value.test((PsiField)referent);
  }
}