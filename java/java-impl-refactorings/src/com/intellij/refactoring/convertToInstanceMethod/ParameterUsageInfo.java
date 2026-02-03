// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;

final class ParameterUsageInfo extends UsageInfo {
  private final PsiReference myReferenceExpression;

  ParameterUsageInfo(PsiReference referenceElement) {
    super(referenceElement);
    myReferenceExpression = referenceElement;
  }

  public PsiReference getReferenceExpression() {
    return myReferenceExpression;
  }
}
