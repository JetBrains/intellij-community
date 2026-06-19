// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class SimplifiableBooleanExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    String replacement = switch (infos[0]) {
      case PsiPrefixExpression prefix -> calculateReplacementExpression(prefix, new CommentTracker());
      case PsiBinaryExpression bin -> calculateReplacementExpression(bin, new CommentTracker());
      case null, default -> throw new AssertionError("should not be reached");
    };
    return InspectionGadgetsBundle.message("boolean.expression.can.be.simplified.problem.descriptor", replacement);
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    return new SimplifiableBooleanExpressionFix();
  }

  private static class SimplifiableBooleanExpressionFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      CommentTracker commentTracker = new CommentTracker();
      final String replacement = switch (element) {
        case PsiPrefixExpression prefix -> calculateReplacementExpression(prefix, commentTracker);
        case PsiBinaryExpression bin -> calculateReplacementExpression(bin, commentTracker);
        default -> null;
      };
      if (replacement != null) {
        PsiReplacementUtil.replaceExpression((PsiExpression)element, replacement, commentTracker);
      }
    }
  }

  static @NonNls String calculateReplacementExpression(PsiPrefixExpression expression, CommentTracker commentTracker) {
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

  static @NonNls String calculateReplacementExpression(PsiBinaryExpression expression, CommentTracker commentTracker) {
    if (!(PsiUtil.skipParenthesizedExprDown(expression.getLOperand()) instanceof PsiPolyadicExpression conjunction)) return null;
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
      conjunctionRemnant = isFirst
                           ? commentTracker.rangeText(operands[1], operands[operands.length - 1])
                           : commentTracker.rangeText(operands[0], operands[operands.length - 2]);
      if (expression.getLOperand() instanceof PsiParenthesizedExpression) {
        conjunctionRemnant = "(" + conjunctionRemnant + ")";
      }
    }
    return isFirst
           ? commentTracker.text(rightDisjunct, ParenthesesUtils.OR_PRECEDENCE) + "||" + conjunctionRemnant
           : conjunctionRemnant + "||" + commentTracker.text(rightDisjunct, ParenthesesUtils.OR_PRECEDENCE);
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new SimplifiableBooleanExpressionVisitor();
  }

  private static class SimplifiableBooleanExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      if (!JavaTokenType.EXCL.equals(expression.getOperationTokenType())
          || !(PsiUtil.skipParenthesizedExprDown(expression.getOperand()) instanceof PsiBinaryExpression maybeXor)
          || !JavaTokenType.XOR.equals(maybeXor.getOperationTokenType())) {
        return;
      }
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
      if (!JavaTokenType.OROR.equals(disjunction.getOperationTokenType())
          || !(PsiUtil.skipParenthesizedExprDown(disjunction.getLOperand()) instanceof PsiPolyadicExpression conjunction)
          || !JavaTokenType.ANDAND.equals(conjunction.getOperationTokenType())) {
        return;
      }

      final PsiExpression rightDisjunct = PsiUtil.skipParenthesizedExprDown(disjunction.getROperand());
      if ((rightDisjunct == null || hasOperand(conjunction, rightDisjunct) )&& !SideEffectChecker.mayHaveSideEffects(conjunction)) {
        registerError(disjunction, disjunction);
      }
      PsiExpression[] operands = conjunction.getOperands();
      if (operands.length >= 2 && (BoolUtils.areExpressionsOpposite(operands[0], rightDisjunct) ||
                                   BoolUtils.areExpressionsOpposite(operands[operands.length - 1], rightDisjunct))
          && !SideEffectChecker.mayHaveSideEffects(rightDisjunct)) {
        registerError(disjunction, disjunction);
      }
    }
  }

  private static boolean hasOperand(PsiPolyadicExpression polyadic, @NotNull PsiExpression operand) {
    EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    return ContainerUtil.exists(polyadic.getOperands(), op -> equivalence.expressionsAreEquivalent(op, operand));
  }
}
