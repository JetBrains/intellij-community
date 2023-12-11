// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.ChangeModifierFix;
import com.siyeh.ig.psiutils.CloneUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class NonPublicCloneInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.public.clone.problem.descriptor");
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ChangeModifierFix(PsiModifier.PUBLIC);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonPublicCloneVisitor();
  }

  private static class NonPublicCloneVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (method.hasModifierProperty(PsiModifier.PUBLIC) || !CloneUtils.isClone(method)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (!CloneUtils.isCloneable(containingClass)) {
        return;
      }
      registerMethodError(method);
    }
  }
}
