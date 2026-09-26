// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiMethodUtil;

public final class ConfigurationUtil {
  public static final Condition<PsiClass> PUBLIC_INSTANTIATABLE_CLASS = psiClass -> {
    return PsiMethodUtil.MAIN_CLASS.value(psiClass) &&
           psiClass.hasModifierProperty(PsiModifier.PUBLIC) &&
           !psiClass.hasModifierProperty(PsiModifier.ABSTRACT);
  };

  /// @deprecated Use {@link PsiMethodUtil#MAIN_CLASS} directly instead.
  @Deprecated
  public static final Condition<PsiClass> MAIN_CLASS = PsiMethodUtil.MAIN_CLASS;
}
