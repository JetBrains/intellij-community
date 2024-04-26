// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DoubleNegationInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("double.negation.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    return new DoubleNegationFix();
  }

  private static class DoubleNegationFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("double.negation.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement expression, @NotNull ModPsiUpdater updater) {
      CommentTracker tracker = new CommentTracker();
      if (expression instanceof PsiPrefixExpression prefixExpression) {
        final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(prefixExpression.getOperand());
        PsiReplacementUtil.replaceExpression(prefixExpression, BoolUtils.getNegatedExpressionText(operand, tracker), tracker);
      } else if (expression instanceof PsiPolyadicExpression polyadicExpression) {
        final PsiExpression[] operands = polyadicExpression.getOperands();
        final int length = operands.length;
        if (length == 2) {
          final PsiExpression firstOperand = operands[0];
          final PsiExpression secondOperand = operands[1];
          if (isNegation(firstOperand)) {
            PsiReplacementUtil
              .replaceExpression(polyadicExpression, BoolUtils.getNegatedExpressionText(firstOperand, tracker) + "==" + tracker.text(secondOperand), tracker);
          }
          else {
            PsiReplacementUtil
              .replaceExpression(polyadicExpression, tracker.text(firstOperand) + "==" + BoolUtils.getNegatedExpressionText(secondOperand, tracker), tracker);
          }
        }
        else {
          final StringBuilder newExpressionText = new StringBuilder();
          for (int i = 0; i < length; i++) {
            if (i > 0) {
              if (length % 2 != 1 && i == length - 1) {
                newExpressionText.append("!=");
              }
              else {
                newExpressionText.append("==");
              }
            }
            newExpressionText.append(tracker.text(operands[i]));
          }
          PsiReplacementUtil.replaceExpression(polyadicExpression, newExpressionText.toString(), tracker);
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DoubleNegationVisitor();
  }

  private static class DoubleNegationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      if (!isUnaryNegation(expression)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (!isNegation(operand)) {
        return;
      }
      PsiExpression nestedOperand = PsiUtil.skipParenthesizedExprDown(operand);
      if (nestedOperand instanceof PsiPrefixExpression) {
        PsiExpression nestedPrefixOperand = ((PsiPrefixExpression)nestedOperand).getOperand();
        if (nestedPrefixOperand == null || !LambdaUtil.isSafeLambdaReturnValueReplacement(expression, nestedPrefixOperand)) {
          return;
        }
      }
      registerError(expression);
    }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      if (!isBinaryNegation(expression)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      if (operands.length == 2) {
        int notNegatedCount = 0;
        for (PsiExpression operand : operands) {
          if (!isNegation(operand)) {
            notNegatedCount++;
          }
        }
        if (notNegatedCount > 1) {
          return;
        }
      }
      registerError(expression);
    }
  }

  public static boolean isNegation(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiPrefixExpression) return isUnaryNegation((PsiPrefixExpression)expression);
    if (expression instanceof PsiPolyadicExpression) return isBinaryNegation((PsiPolyadicExpression)expression);
    return false;
  }

  static boolean isUnaryNegation(PsiPrefixExpression expression) {
    return JavaTokenType.EXCL.equals(expression.getOperationTokenType());
  }

  static boolean isBinaryNegation(PsiPolyadicExpression expression) {
    PsiExpression[] operands = expression.getOperands();
    if (operands.length == 1) return false;
    for (PsiExpression operand : operands) {
      if (TypeUtils.hasFloatingPointType(operand)) return false; // don't change semantics for NaNs
    }
    return JavaTokenType.NE.equals(expression.getOperationTokenType());
  }
}