// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class BooleanExpressionMayBeConditionalInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("if.may.be.conditional.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new BooleanExpressionMayBeConditionalFix();
  }

  private static class BooleanExpressionMayBeConditionalFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("if.may.be.conditional.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater)  {
      if (!(element instanceof PsiBinaryExpression binaryExpression)) {
        return;
      }
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
      if (!(lhs instanceof PsiBinaryExpression lBinaryExpression) || !(rhs instanceof PsiBinaryExpression rBinaryExpression)) {
        return;
      }
      final PsiExpression llhs = PsiUtil.skipParenthesizedExprDown(lBinaryExpression.getLOperand());
      final PsiExpression lrhs = PsiUtil.skipParenthesizedExprDown(rBinaryExpression.getLOperand());
      if (llhs == null || lrhs == null) {
        return;
      }
      final PsiExpression thenExpression = PsiUtil.skipParenthesizedExprDown(lBinaryExpression.getROperand());
      final PsiExpression elseExpression = PsiUtil.skipParenthesizedExprDown(rBinaryExpression.getROperand());
      if (thenExpression == null || elseExpression == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      if (BoolUtils.isNegation(llhs) ) {
        PsiReplacementUtil.replaceExpression(binaryExpression,
                                             getText(lrhs, commentTracker) + '?' + getText(elseExpression, commentTracker) + ':' + getText(thenExpression, commentTracker),
                                             commentTracker);
      }
      else {
        PsiReplacementUtil.replaceExpression(binaryExpression,
                                             getText(llhs, commentTracker) + '?' + getText(thenExpression, commentTracker) + ':' + getText(elseExpression, commentTracker),
                                             commentTracker);
      }
    }

    private static String getText(@NotNull PsiExpression expression, CommentTracker commentTracker) {
      return ParenthesesUtils.getText(commentTracker.markUnchanged(expression), ParenthesesUtils.CONDITIONAL_PRECEDENCE);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BooleanExpressionMayBeConditionalVisitor();
  }

  private static class BooleanExpressionMayBeConditionalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.OROR.equals(tokenType)) {
        return;
      }
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
      if (!(lhs instanceof PsiBinaryExpression lBinaryExpression) || !(rhs instanceof PsiBinaryExpression rBinaryExpression)) {
        return;
      }
      final IElementType lTokenType = lBinaryExpression.getOperationTokenType();
      final IElementType rTokenType = rBinaryExpression.getOperationTokenType();
      if (!JavaTokenType.ANDAND.equals(lTokenType) || !JavaTokenType.ANDAND.equals(rTokenType)) {
        return;
      }
      final PsiExpression expression1 = PsiUtil.skipParenthesizedExprDown(lBinaryExpression.getLOperand());
      final PsiExpression expression2 = PsiUtil.skipParenthesizedExprDown(rBinaryExpression.getLOperand());
      if (BoolUtils.areExpressionsOpposite(expression1, expression2) && !SideEffectChecker.mayHaveSideEffects(expression1)) {
        registerError(expression);
      }
    }
  }
}
