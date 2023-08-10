// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.usageView.UsageInfo;

final class MethodCallUsageInfo extends UsageInfo {
  private final PsiMethodCallExpression myMethodCall;

  MethodCallUsageInfo(PsiMethodCallExpression methodCall) {
    super(methodCall);
    myMethodCall = methodCall;
  }

  public PsiMethodCallExpression getMethodCall() {
    return myMethodCall;
  }
}
