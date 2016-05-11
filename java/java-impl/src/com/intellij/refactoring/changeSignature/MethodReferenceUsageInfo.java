/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;

class MethodReferenceUsageInfo extends UsageInfo {

  private final boolean myIsToModifyArgs;
  private final boolean myIsToCatchExceptions;
  
  private PsiCallExpression myCallExpression;

  public MethodReferenceUsageInfo(PsiElement element, PsiMethod method, boolean isToModifyArgs, boolean isToCatchExceptions) {
    super(element);
    myIsToModifyArgs = isToModifyArgs;
    myIsToCatchExceptions = isToCatchExceptions;
  }

  public void setCallExpression(PsiCallExpression callExpression) {
    myCallExpression = callExpression;
  }

  @Nullable
  public MethodCallUsageInfo createMethodCallInfo() {
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

  @Nullable
  @Override
  public PsiElement getElement() {
    if (myCallExpression != null) {
      return myCallExpression;
    }
    return super.getElement();
  }
}
