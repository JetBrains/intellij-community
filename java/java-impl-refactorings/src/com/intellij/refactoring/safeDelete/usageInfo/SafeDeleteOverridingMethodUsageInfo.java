// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class SafeDeleteOverridingMethodUsageInfo extends SafeDeleteUsageInfo implements SafeDeleteCustomUsageInfo, ModPlanSafeDeleteUsage {
  private final @Nullable String myExternalName;

  public SafeDeleteOverridingMethodUsageInfo(PsiMethod overridingMethod, PsiMethod method) {
    super(overridingMethod, method);
    myExternalName = PsiFormatUtil.getExternalName(overridingMethod);
  }

  public @Nullable PsiElement getOverridingMethod() {
    return getElement();
  }

  /**
   * @return the external name of the overriding method; a light method of another language has one too, though its element is the source
   */
  public @Nullable String getExternalName() {
    return myExternalName;
  }

  @Override
  public void performRefactoring() throws IncorrectOperationException {
    PsiElement element = getElement();
    if (element != null) {
      element.delete();
    }
  }

  @Override
  public @Nullable ModPlanEdit asModPlanEdit() {
    PsiElement element = getElement();
    return element == null ? null : ModPlanEdit.delete(element);
  }
}
