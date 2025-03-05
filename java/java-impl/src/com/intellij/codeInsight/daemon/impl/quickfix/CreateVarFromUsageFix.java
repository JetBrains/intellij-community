// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.*;

public abstract class CreateVarFromUsageFix extends CreateFromUsageBaseFix {
  protected final SmartPsiElementPointer<PsiReferenceExpression> myReferenceExpression;

  public CreateVarFromUsageFix(PsiReferenceExpression referenceElement) {
    myReferenceExpression = SmartPointerManager.createPointer(referenceElement);
  }

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiReferenceExpression expression = (PsiReferenceExpression)element;
    return CreateFromUsageUtils.isValidReference(expression, false);
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return false;
  }

  @Override
  protected PsiElement getElement() {
    PsiReferenceExpression element = myReferenceExpression.getElement();
    if (element == null) return null;
    if (!element.isValid() || !canModify(element)) return null;

    PsiElement parent = element.getParent();

    if (parent instanceof PsiMethodCallExpression) return null;

    if (element.getReferenceNameElement() != null) {
      if (!CreateFromUsageUtils.isValidReference(element, false)) {
        return element;
      }
    }

    return null;
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    PsiReferenceExpression element = myReferenceExpression.getElement();
    if (element == null) return false;
    setText(getText(element.getReferenceName()));
    return true;
  }

  protected abstract @IntentionName String getText(String varName);
}
