// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;

public class OverriderUsageInfo extends UsageInfo implements OverriderMethodUsageInfo<PsiMethod> {
  private final PsiMethod myBaseMethod;
  private final boolean myToInsertArgs;
  private final boolean myToCatchExceptions;
  private final boolean myIsOriginalOverrider;
  private final PsiMethod myOverridingMethod;

  public OverriderUsageInfo(final PsiMethod method, PsiMethod baseMethod, boolean  isOriginalOverrider,
                            boolean toInsertArgs, boolean toCatchExceptions) {
    super(method);
    myOverridingMethod = method;
    myBaseMethod = baseMethod;
    myToInsertArgs = toInsertArgs;
    myToCatchExceptions = toCatchExceptions;
    myIsOriginalOverrider = isOriginalOverrider;
  }

  @Override
  public PsiMethod getBaseMethod() {
    return myBaseMethod;
  }

  @Override
  public PsiMethod getOverridingMethod() {
    return myOverridingMethod;
  }

  /**
   * @deprecated use {@link #getOverridingMethod()} instead
   */
  @Deprecated
  @Override
  public @Nullable PsiMethod getElement() {
    PsiElement element = super.getElement();
    return element instanceof PsiMethod ? (PsiMethod)element : myOverridingMethod;
  }

  public boolean isOriginalOverrider() {
    return myIsOriginalOverrider;
  }

  public boolean isToCatchExceptions() {
    return myToCatchExceptions;
  }

  public boolean isToInsertArgs() {
    return myToInsertArgs;
  }
}
