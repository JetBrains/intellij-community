// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.usageView.UsageInfo;

public final class MethodReferenceUsageInfo extends UsageInfo {
  private final PsiMethodReferenceExpression myExpression;
  private final boolean myApplicableBySecondSearch;
  private PsiMethodCallExpression myReplacement;

  public MethodReferenceUsageInfo(PsiMethodReferenceExpression methodReferenceExpression, boolean bySecondSearch) {
    super(methodReferenceExpression);
    myExpression = methodReferenceExpression;
    myApplicableBySecondSearch = bySecondSearch;
  }

  public PsiMethodReferenceExpression getExpression() {
    return myExpression;
  }

  public boolean isApplicableBySecondSearch() {
    return myApplicableBySecondSearch;
  }

  public void setReplacement(PsiMethodCallExpression replacement) {
    myReplacement = replacement;
  }

  public PsiMethodCallExpression getReplacement() {
    return myReplacement;
  }
}
