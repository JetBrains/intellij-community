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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
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
  @Nullable
  @Override
  public PsiMethod getElement() {
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
