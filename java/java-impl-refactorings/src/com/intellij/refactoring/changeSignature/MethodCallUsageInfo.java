// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

public class MethodCallUsageInfo extends UsageInfo {
  private final boolean myToChangeArguments;
  private final boolean myToCatchExceptions;
  private final boolean myVarArgCall;
  private final PsiMethod myReferencedMethod;
  private final PsiSubstitutor mySubstitutor;

  public boolean isToCatchExceptions() {
    return myToCatchExceptions;
  }

  public boolean isToChangeArguments() {
    return myToChangeArguments;
  }

  public boolean isVarArgCall() {
    return myVarArgCall;
  }

  public MethodCallUsageInfo(final @NotNull PsiElement ref, boolean isToChangeArguments, boolean isToCatchExceptions) {
    super(ref);
    myToChangeArguments = isToChangeArguments;
    myToCatchExceptions = isToCatchExceptions;
    PsiCall call = getCall(ref);
    if (call == null) {
      throw new IllegalArgumentException("Unknown reference: " + ref.getClass());
    }
    myVarArgCall = MethodCallUtils.isVarArgCall(call);
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    if (resolveResult == null || !(resolveResult.getElement() instanceof PsiMethod method)) {
      throw new IllegalArgumentException("Cannot resolve call to a method " + call);
    }
    myReferencedMethod = method;
    mySubstitutor = resolveResult.getSubstitutor();
  }

  private static PsiCall getCall(final PsiElement ref) {
    if (ref instanceof PsiCall call) {
      return call;
    }
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiCall call) {
      return call;
    }
    else if (parent instanceof PsiAnonymousClass) {
      return (PsiCall) parent.getParent();
    }
    return null;
  }

  public PsiMethod getReferencedMethod() {
    return myReferencedMethod;
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }
}
