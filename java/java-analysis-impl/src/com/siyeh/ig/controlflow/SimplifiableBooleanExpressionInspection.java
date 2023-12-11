// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Bas Leijdekkers
 */
public final class SimplifiableBooleanExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Object info = infos[0];
    if (info instanceof PsiPrefixExpression prefixExpression) {
      return InspectionGadgetsBundle.message("boolean.expression.can.be.simplified.problem.descriptor",
                                             calculateReplacementExpression(prefixExpression, new CommentTracker()));
    }
    else {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)info;
      return InspectionGadgetsBundle.message("boolean.expression.can.be.simplified.problem.descriptor",
                                             calculateReplacementExpression(binaryExpression, new CommentTracker()));
    }
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new SimplifiableBooleanExpressionFix();
  }

  private static class SimplifiableBooleanExpressionFix extends PsiUpdateModCommandQuickFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      CommentTracker commentTracker = new CommentTracker();
      final String replacement;
      if (element instanceof PsiPrefixExpression prefixExpression) {
        replacement = calculateReplacementExpression(prefixExpression, commentTracker);
      }
      else if (element instanceof PsiBinaryExpression binaryExpression) {
        replacement = calculateReplacementExpression(binaryExpression, commentTracker);
      }
      else {
        return;
      }
      if (replacement == null) {
        return;
      }

      PsiReplacementUtil.replaceExpression((PsiExpression)element, replacement, commentTracker);
    }
  }

  @NonNls
  static String calculateReplacementExpression(PsiPrefixExpression expression, CommentTracker commentTracker) {
    final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
    if (!(operand instanceof PsiBinaryExpression binaryExpression)) {
      return null;
    }
    final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
    if (lhs == null || rhs == null) {
      return null;
    }
    return ParenthesesUtils.getText(commentTracker.markUnchanged(lhs), ParenthesesUtils.EQUALITY_PRECEDENCE) + "==" +
           ParenthesesUtils.getText(commentTracker.markUnchanged(rhs), ParenthesesUtils.EQUALITY_PRECEDENCE);
  }

  @NonNls
  static String calculateReplacementExpression(PsiBinaryExpression expression, CommentTracker commentTracker) {
    PsiPolyadicExpression conjunction =
      tryCast(PsiUtil.skipParenthesizedExprDown(expression.getLOperand()), PsiPolyadicExpression.class);
    if (conjunction == null) return null;
    final PsiExpression rightDisjunct = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
    if (rightDisjunct == null) return null;

    if (hasOperand(conjunction, rightDisjunct)) return commentTracker.text(rightDisjunct);
    PsiExpression[] operands = conjunction.getOperands();
    if (operands.length < 2) return null; // incomplete
    boolean isFirst;
    if (BoolUtils.areExpressionsOpposite(operands[0], rightDisjunct)) {
      isFirst = true;
    }
    else if (BoolUtils.areExpressionsOpposite(operands[operands.length - 1], rightDisjunct)) {
      isFirst = false;
    }
    else {
      return null;
    }
    String conjunctionRemnant;
    if (operands.length == 2) {
      conjunctionRemnant = commentTracker.text(operands[isFirst ? 1 : 0], ParenthesesUtils.OR_PRECEDENCE);
    }
    else {
      if (isFirst) {
        conjunctionRemnant = commentTracker.rangeText(operands[1], operands[operands.length - 1]);
      }
      else {
        conjunctionRemnant = commentTracker.rangeText(operands[0], operands[operands.length - 2]);
      }
      if (expression.getLOperand() instanceof PsiParenthesizedExpression) {
        conjunctionRemnant = "(" + conjunctionRemnant + ")";
      }
    }
    return isFirst ?
           commentTracker.text(rightDisjunct, ParenthesesUtils.OR_PRECEDENCE) + "||" + conjunctionRemnant :
           conjunctionRemnant + "||" + commentTracker.text(rightDisjunct, ParenthesesUtils.OR_PRECEDENCE);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableBooleanExpressionVisitor();
  }

  private static class SimplifiableBooleanExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      if (!JavaTokenType.EXCL.equals(expression.getOperationTokenType())) return;

      PsiBinaryExpression maybeXor = tryCast(PsiUtil.skipParenthesizedExprDown(expression.getOperand()), PsiBinaryExpression.class);
      if (maybeXor == null || !JavaTokenType.XOR.equals(maybeXor.getOperationTokenType())) return;

      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(maybeXor.getLOperand());
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(maybeXor.getROperand());
      if (lhs == null || rhs == null) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression disjunction) {
      super.visitBinaryExpression(disjunction);
      if (!JavaTokenType.OROR.equals(disjunction.getOperationTokenType())) return;
      PsiPolyadicExpression conjunction =
        tryCast(PsiUtil.skipParenthesizedExprDown(disjunction.getLOperand()), PsiPolyadicExpression.class);
      if (conjunction == null || !JavaTokenType.ANDAND.equals(conjunction.getOperationTokenType())) return;

      final PsiExpression rightDisjunct = PsiUtil.skipParenthesizedExprDown(disjunction.getROperand());
      if (hasOperand(conjunction, rightDisjunct) && !SideEffectChecker.mayHaveSideEffects(conjunction)) {
        registerError(disjunction, disjunction);
      }
      PsiExpression[] operands = conjunction.getOperands();
      if (operands.length >= 2 && (BoolUtils.areExpressionsOpposite(operands[0], rightDisjunct) ||
           BoolUtils.areExpressionsOpposite(operands[operands.length - 1], rightDisjunct)) &&
          !SideEffectChecker.mayHaveSideEffects(rightDisjunct)) {
        registerError(disjunction, disjunction);
      }
    }
  }

  private static boolean hasOperand(PsiPolyadicExpression polyadic, PsiExpression operand) {
    if (operand == null) return false;
    EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    return Arrays.stream(polyadic.getOperands()).anyMatch(op -> equivalence.expressionsAreEquivalent(op, operand));
  }
}
