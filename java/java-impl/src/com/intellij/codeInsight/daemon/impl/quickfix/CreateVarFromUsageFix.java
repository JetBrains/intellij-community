// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;

public abstract class CreateVarFromUsageFix extends CreateFromUsageBaseFix {
  protected final PsiReferenceExpression myReferenceExpression;

  public CreateVarFromUsageFix(PsiReferenceExpression referenceElement) {
    myReferenceExpression = referenceElement;
  }

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiReferenceExpression expression = (PsiReferenceExpression) element;
    return CreateFromUsageUtils.isValidReference(expression, false);
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return false;
  }

  @Override
  protected PsiElement getElement() {
    if (!myReferenceExpression.isValid() || !canModify(myReferenceExpression)) return null;

    PsiElement parent = myReferenceExpression.getParent();

    if (parent instanceof PsiMethodCallExpression) return null;

    if (myReferenceExpression.getReferenceNameElement() != null) {
      if (!CreateFromUsageUtils.isValidReference(myReferenceExpression, false)) {
        return myReferenceExpression;
      }
    }

    return null;
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    setText(getText(myReferenceExpression.getReferenceName()));
    return true;
  }

  protected abstract @IntentionName String getText(String varName);
}
