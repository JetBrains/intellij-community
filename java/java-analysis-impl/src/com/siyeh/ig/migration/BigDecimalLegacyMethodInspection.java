// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class BigDecimalLegacyMethodInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("bigdecimal.legacy.method.problem.descriptor");
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final Object value = ExpressionUtils.computeConstantExpression(expression);
    if (!(value instanceof  Integer)) {
      return null;
    }
    final int roundingMode = ((Integer)value).intValue();
    if (roundingMode < 0 || roundingMode > 7) {
      return null;
    }
    return new BigDecimalLegacyMethodFix();
  }

  private static class BigDecimalLegacyMethodFix extends PsiUpdateModCommandQuickFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("bigdecimal.legacy.method.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement grandParent = element.getParent().getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 2 && arguments.length != 3) {
        return;
      }
      final PsiExpression argument = arguments[arguments.length - 1];
      final Object value = ExpressionUtils.computeConstantExpression(argument);
      if (!(value instanceof Integer)) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      final int roundingMode = (Integer)value;
      switch (roundingMode) {
        case 0 -> PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.UP", commentTracker);
        case 1 -> PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.DOWN", commentTracker);
        case 2 -> PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.CEILING", commentTracker);
        case 3 -> PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.FLOOR", commentTracker);
        case 4 -> PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.HALF_UP", commentTracker);
        case 5 -> PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.HALF_DOWN", commentTracker);
        case 6 -> PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.HALF_EVEN", commentTracker);
        case 7 -> PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.UNNECESSARY", commentTracker);
      }
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BigDecimalLegacyMethodVisitor();
  }

  private static class BigDecimalLegacyMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final @NonNls String name = methodExpression.getReferenceName();
      if (!"setScale".equals(name) && !"divide".equals(name)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (PsiUtilCore.hasErrorElementChild(argumentList)) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 2 && arguments.length != 3) {
        return;
      }
      final PsiExpression argument = arguments[arguments.length - 1];
      if (!PsiTypes.intType().equals(argument.getType())) {
        return;
      }
      if (!TypeUtils.expressionHasTypeOrSubtype(expression, "java.math.BigDecimal")) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || !"java.math.BigDecimal".equals(containingClass.getQualifiedName())) {
        return;
      }
      registerMethodCallError(expression, argument);
    }
  }
}
