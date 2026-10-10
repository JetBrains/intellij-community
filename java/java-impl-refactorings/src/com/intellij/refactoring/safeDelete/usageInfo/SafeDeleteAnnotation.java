// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class SafeDeleteAnnotation extends SafeDeleteReferenceUsageInfo implements ModPlanSafeDeleteUsage {
  public SafeDeleteAnnotation(PsiAnnotation element, PsiElement referencedElement, boolean safeDelete) {
    super(element, referencedElement, safeDelete);
  }

  @Override
  public void deleteElement() throws IncorrectOperationException {
    final PsiElement element = getElement();
    if (element != null) {
      element.delete();
    }
  }

  @Override
  public @Nullable ModPlanEdit asModPlanEdit() {
    if (!isSafeDelete() || !(getElement() instanceof PsiAnnotation annotation)) return null;
    return ModPlanEdit.update(annotation, PsiElement::delete);
  }
}
