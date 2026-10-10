// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SafeDeleteOverrideAnnotation extends SafeDeleteUsageInfo implements SafeDeleteCustomUsageInfo, ModPlanSafeDeleteUsage {
  public SafeDeleteOverrideAnnotation(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }

  public PsiMethod getMethod() {
    return (PsiMethod)getElement();
  }

  @Override
  public void performRefactoring() throws IncorrectOperationException {
    // An earlier usage may have removed the method: there is nothing to write.
    PsiMethod method = getMethod();
    if (method != null) removeOverride(method);
  }

  static void removeOverride(@NotNull PsiMethod method) throws IncorrectOperationException {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, true, Override.class.getName());
    if (annotation != null) {
      annotation.delete();
    }
  }

  @Override
  public @Nullable ModPlanEdit asModPlanEdit() {
    if (!(getElement() instanceof PsiMethod method)) return null;
    return ModPlanEdit.update(method, SafeDeleteOverrideAnnotation::removeOverride);
  }
}
