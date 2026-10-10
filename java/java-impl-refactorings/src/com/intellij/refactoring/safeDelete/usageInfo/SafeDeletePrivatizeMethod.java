// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class SafeDeletePrivatizeMethod extends SafeDeleteOverrideAnnotation {
  public SafeDeletePrivatizeMethod(PsiMethod method, PsiMethod overridenMethod) {
    super(method, overridenMethod);
  }

  @Override
  public void performRefactoring() throws IncorrectOperationException {
    PsiUtil.setModifierProperty(getMethod(), PsiModifier.PRIVATE, true);
    super.performRefactoring();
  }

  @Override
  public @Nullable ModPlanEdit asModPlanEdit() {
    if (!(getElement() instanceof PsiMethod method) || !(findReferencedElement() instanceof PsiMethod)) return null;
    return ModPlanEdit.update(method, copy -> {
      PsiUtil.setModifierProperty(copy, PsiModifier.PRIVATE, true);
      removeOverride(copy);
    });
  }
}
