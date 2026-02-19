// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;

class MethodReferenceUsageInfo extends UsageInfo {

  private final boolean myIsToModifyArgs;
  private final boolean myIsToCatchExceptions;
  
  private PsiCallExpression myCallExpression;

  MethodReferenceUsageInfo(PsiElement element, boolean isToModifyArgs, boolean isToCatchExceptions) {
    super(element);
    myIsToModifyArgs = isToModifyArgs;
    myIsToCatchExceptions = isToCatchExceptions;
  }

  public void setCallExpression(PsiCallExpression callExpression) {
    myCallExpression = callExpression;
  }

  public @Nullable MethodCallUsageInfo createMethodCallInfo() {
    if (myCallExpression == null) {
      return null;
    }
    return new MethodCallUsageInfo(myCallExpression, myIsToModifyArgs, myIsToCatchExceptions);
  }

  public static boolean needToExpand(JavaChangeInfo changeInfo) {
    if (!changeInfo.isGenerateDelegate()) {
      if (changeInfo.isParameterSetOrOrderChanged()) {
        return true;
      }
      else if (changeInfo.isExceptionSetOrOrderChanged()) {
        return JavaChangeSignatureUsageProcessor.hasNewCheckedExceptions(changeInfo);
      }
    }

    return false;
  }

  @Override
  public @Nullable PsiElement getElement() {
    if (myCallExpression != null) {
      return myCallExpression;
    }
    return super.getElement();
  }
}
