// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.AddThisQualifierFix;
import org.jetbrains.annotations.NotNull;

public final class UnqualifiedMethodAccessInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnqualifiedMethodAccessVisitor();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unqualified.method.access.problem.descriptor");
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    final PsiReferenceExpression expressionToQualify = (PsiReferenceExpression)infos[0];
    final PsiMethod methodAccessed = (PsiMethod)infos[1];
    return AddThisQualifierFix.buildFix(expressionToQualify, methodAccessed);
  }

  private static class UnqualifiedMethodAccessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      if (methodExpression.getQualifierExpression() != null) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null || method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      registerError(methodExpression, methodExpression, method);
    }
  }
}