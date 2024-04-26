// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.redundantCast.RemoveRedundantCastUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;

public final class MathRoundingWithIntArgumentInspection extends BaseInspection {

  private static final CallMatcher MATH_ROUNDING_MATCHERS =
    CallMatcher.anyOf(
      CallMatcher.staticCall("java.lang.Math", "round", "ceil", "floor", "rint").parameterCount(1),
      CallMatcher.staticCall("java.lang.StrictMath", "round", "ceil", "floor", "rint").parameterCount(1));

  private static final CallMatcher QUICK_FIX_MATH_ROUNDING_MATCHERS =
    CallMatcher.anyOf(
      CallMatcher.staticCall("java.lang.Math", "ceil", "floor", "rint").parameterCount(1),
      CallMatcher.staticCall("java.lang.StrictMath", "ceil", "floor", "rint").parameterCount(1));

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "math.rounding.with.int.argument.problem.descriptor");
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)infos[0];
    if (!QUICK_FIX_MATH_ROUNDING_MATCHERS.matches(callExpression)) {
      return null;
    }
    return new MathRoundingWithIntArgumentFix(callExpression.getMethodExpression().getText());
  }

  private static class MathRoundingWithIntArgumentFix extends PsiUpdateModCommandQuickFix {

    private final String method;

    private MathRoundingWithIntArgumentFix(@NlsSafe String method) {
      this.method = method;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("math.rounding.with.int.argument.quickfix", method);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("math.rounding.with.int.argument.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = element.getParent();
      if (parent == null) return;
      if (!(parent.getParent() instanceof PsiMethodCallExpression callExpression)) {
        return;
      }
      PsiExpressionList argumentList = callExpression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      PsiExpression argument = arguments[0];
      CommentTracker ct = new CommentTracker();
      PsiTypeCastExpression castExpression = (PsiTypeCastExpression)ct
        .replaceAndRestoreComments(callExpression, "(double)" + ct.text(argument, PsiPrecedenceUtil.TYPE_CAST_PRECEDENCE));
      if (RedundantCastUtil.isCastRedundant(castExpression)) {
        RemoveRedundantCastUtil.removeCast(castExpression);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MathRoundingWithIntArgumentVisitor();
  }

  private static class MathRoundingWithIntArgumentVisitor
    extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MATH_ROUNDING_MATCHERS.matches(expression)) return;
      PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      if (arguments.length != 1) {
        return;
      }
      PsiExpression argument = arguments[0];
      PsiType type = argument.getType();
      if (type == null) return;
      if (!PsiTypes.intType().equals(type) && !type.equalsToText(JAVA_LANG_INTEGER)) {
        return;
      }

      registerMethodCallError(expression, expression);
    }
  }
}