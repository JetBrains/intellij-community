// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.psi.PsiClass;
import com.intellij.usageView.UsageInfo;

public final class ImplementingClassUsageInfo extends UsageInfo {
  private final PsiClass myClass;
  public ImplementingClassUsageInfo(PsiClass aClass) {
    super(aClass);
    myClass = aClass;
  }

  public PsiClass getPsiClass() {
    return myClass;
  }
}
