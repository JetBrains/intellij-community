// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;

class ConvertCatchToThrowsPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiCatchSection)) {
      return false;
    }
    if (element instanceof PsiCodeBlock) {
      return false;
    }
    final PsiElement owner = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiClass.class, PsiLambdaExpression.class);
    if (owner instanceof PsiLambdaExpression) {
      final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(owner);
      return !(method instanceof PsiCompiledElement);
    }
    return owner instanceof PsiMethod;
  }
}
