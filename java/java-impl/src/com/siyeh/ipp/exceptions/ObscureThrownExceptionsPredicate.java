// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.exceptions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceList;
import com.siyeh.ipp.base.PsiElementPredicate;

class ObscureThrownExceptionsPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiReferenceList throwsList)) {
      return false;
    }
    if (throwsList.getReferenceElements().length < 2) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiMethod method)) {
      return false;
    }
    return method.getThrowsList().equals(element);
  }
}
