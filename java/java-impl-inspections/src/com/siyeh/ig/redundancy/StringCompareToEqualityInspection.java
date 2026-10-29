// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StringCompareToEqualityInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher STRING_COMPARE_TO =
    CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_STRING, "compareTo").parameterTypes(CommonClassNames.JAVA_LANG_STRING);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
        super.visitBinaryExpression(expression);
        final IElementType tokenType = expression.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.EQEQ) && !tokenType.equals(JavaTokenType.NE)) return;
        final PsiMethodCallExpression call = compareToComparedWithZero(expression);
        if (call == null) return;
        final PsiExpression argument = call.getArgumentList().getExpressions()[0];
        // compareTo(null) throws a NullPointerException, but equals(null) returns false, so the replacement
        // would change behavior when the argument may be null.
        if (NullabilityUtil.getExpressionNullability(argument) == Nullability.NULLABLE) return;
        holder.registerProblem(expression,
                               InspectionGadgetsBundle.message("string.compare.to.equality.problem.descriptor"),
                               new ReplaceWithEqualsFix());
      }
    };
  }

  /** Returns the 'String.compareTo(String)' call if one operand is such a call and the other is a literal 0. */
  private static @Nullable PsiMethodCallExpression compareToComparedWithZero(@NotNull PsiBinaryExpression expression) {
    final PsiExpression left = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
    final PsiExpression right = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
    if (left == null || right == null) return null;
    final PsiMethodCallExpression leftCall = asStringCompareTo(left);
    if (leftCall != null && ExpressionUtils.isZero(right)) return leftCall;
    final PsiMethodCallExpression rightCall = asStringCompareTo(right);
    if (rightCall != null && ExpressionUtils.isZero(left)) return rightCall;
    return null;
  }

  private static @Nullable PsiMethodCallExpression asStringCompareTo(@NotNull PsiExpression expression) {
    if (expression instanceof PsiMethodCallExpression call &&
        STRING_COMPARE_TO.test(call) &&
        call.getMethodExpression().getQualifierExpression() != null) {
      return call;
    }
    return null;
  }

  private static class ReplaceWithEqualsFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("string.compare.to.equality.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiBinaryExpression expression)) return;
      final boolean negated = expression.getOperationTokenType().equals(JavaTokenType.NE);
      final PsiMethodCallExpression call = compareToComparedWithZero(expression);
      if (call == null) return;
      final PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      final PsiExpression argument = call.getArgumentList().getExpressions()[0];
      final CommentTracker ct = new CommentTracker();
      final String replacement = (negated ? "!" : "") + ct.text(qualifier) + ".equals(" + ct.text(argument) + ")";
      ct.replaceAndRestoreComments(expression, replacement);
    }
  }
}
