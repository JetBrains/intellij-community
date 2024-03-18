// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AmbiguousMethodCallInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiClass superClass = (PsiClass)infos[0];
    final PsiClass outerClass = (PsiClass)infos[1];
    return InspectionGadgetsBundle.message("ambiguous.method.call.problem.descriptor", superClass.getName(), outerClass.getName());
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    return new AmbiguousMethodCallFix();
  }

  private static class AmbiguousMethodCallFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("ambiguous.method.call.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent.getParent();
      final String newExpressionText = "super." + methodCallExpression.getText();
      PsiReplacementUtil.replaceExpression(methodCallExpression, newExpressionText);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AmbiguousMethodCallVisitor();
  }

  private static class AmbiguousMethodCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null) {
        return;
      }
      PsiClass containingClass = ClassUtils.getContainingClass(expression);
      if (containingClass == null) {
        return;
      }
      final PsiMethod targetMethod = expression.resolveMethod();
      if (targetMethod == null) {
        return;
      }
      final PsiClass methodClass = targetMethod.getContainingClass();
      if (methodClass == null || !containingClass.isInheritor(methodClass, true)) {
        return;
      }
      boolean staticAccess = containingClass.hasModifierProperty(PsiModifier.STATIC);
      containingClass = ClassUtils.getContainingClass(containingClass);
      while (containingClass != null) {
        staticAccess |= containingClass.hasModifierProperty(PsiModifier.STATIC);
        final PsiMethod[] methods = containingClass.findMethodsBySignature(targetMethod, false);
        if (methods.length > 0 && !methodClass.equals(containingClass)) {
          if (!staticAccess || ContainerUtil.exists(methods, m -> m.hasModifierProperty(PsiModifier.STATIC))) {
            registerMethodCallError(expression, methodClass, containingClass);
            return;
          }
        }
        containingClass = ClassUtils.getContainingClass(containingClass);
      }
    }
  }
}