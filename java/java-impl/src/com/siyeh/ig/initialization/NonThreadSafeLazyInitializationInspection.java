// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.initialization;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.IntroduceHolderFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class NonThreadSafeLazyInitializationInspection extends BaseInspection {

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiIfStatement ifStatement = (PsiIfStatement)infos[0];
    final PsiField field = (PsiField)infos[1];
    return IntroduceHolderFix.createFix(field, ifStatement);
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.thread.safe.lazy.initialization.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnsafeSafeLazyInitializationVisitor();
  }

  private static class UnsafeSafeLazyInitializationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression reference)) {
        return;
      }
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getRExpression());
      if (rhs == null) {
        return;
      }
      final PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiField field)) {
        return;
      }
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (isInStaticInitializer(expression)) {
        return;
      }
      if (isInSynchronizedContext(expression)) {
        return;
      }
      final PsiStatement statement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(statement, PsiCodeBlock.class, PsiBlockStatement.class);
      if (!(parent instanceof PsiIfStatement ifStatement)) {
        return;
      }
      final PsiExpression condition = ifStatement.getCondition();
      if (!ComparisonUtils.isNullComparison(condition, field, true)) {
        return;
      }
      registerError(lhs, ifStatement, field);
    }

    private static boolean isInSynchronizedContext(PsiElement element) {
      final PsiSynchronizedStatement syncBlock = PsiTreeUtil.getParentOfType(element, PsiSynchronizedStatement.class);
      if (syncBlock != null) {
        return true;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      return method != null && method.hasModifierProperty(PsiModifier.SYNCHRONIZED) && method.hasModifierProperty(PsiModifier.STATIC);
    }

    private static boolean isInStaticInitializer(PsiElement element) {
      final PsiClassInitializer initializer = PsiTreeUtil.getParentOfType(element, PsiClassInitializer.class);
      return initializer != null && initializer.hasModifierProperty(PsiModifier.STATIC);
    }
  }
}
