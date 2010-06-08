/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;

/**
 * @author ven
 */
public class MethodCallUsageInfo extends UsageInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.MethodCallUsageInfo");
  private final boolean myToChangeArguments;
  private final boolean myToCatchExceptions;
  private final PsiMethod myReferencedMethod;
  private final PsiSubstitutor mySubstitutor;

  public boolean isToCatchExceptions() {
    return myToCatchExceptions;
  }

  public boolean isToChangeArguments() {
    return myToChangeArguments;
  }

  public MethodCallUsageInfo(final PsiElement ref, boolean isToChangeArguments, boolean isToCatchExceptions) {
    super(ref);
    myToChangeArguments = isToChangeArguments;
    myToCatchExceptions = isToCatchExceptions;
    final JavaResolveResult resolveResult = resolveMethod(ref);
    myReferencedMethod = (PsiMethod)resolveResult.getElement();
    mySubstitutor = resolveResult.getSubstitutor();
  }

  private static JavaResolveResult resolveMethod(final PsiElement ref) {
    if (ref instanceof PsiEnumConstant) return ((PsiEnumConstant)ref).resolveMethodGenerics();
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiCall) {
      return ((PsiCall)parent).resolveMethodGenerics();
    }
    else if (parent instanceof PsiAnonymousClass) {
      return ((PsiNewExpression)parent.getParent()).resolveMethodGenerics();
    }
    LOG.error("Unknown reference");

    return null;
  }

  public PsiMethod getReferencedMethod() {
    return myReferencedMethod;
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }
}
