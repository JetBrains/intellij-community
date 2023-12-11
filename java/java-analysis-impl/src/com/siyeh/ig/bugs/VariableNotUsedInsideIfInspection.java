// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public final class VariableNotUsedInsideIfInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final boolean isIf = ((Boolean)infos[0]).booleanValue();
    if (isIf) {
      return InspectionGadgetsBundle.message("variable.not.used.inside.if.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("variable.not.used.inside.conditional.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new VariableNotUsedInsideIfVisitor();
  }

  private static class VariableNotUsedInsideIfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(expression.getCondition());
      if (!(condition instanceof PsiBinaryExpression binaryExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = extractVariableReference(binaryExpression);
      if (referenceExpression == null) {
        return;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (tokenType == JavaTokenType.EQEQ) {
        if (checkVariableUsage(referenceExpression, expression.getThenExpression(), expression.getElseExpression())) {
          registerError(referenceExpression, Boolean.FALSE);
        }
      }
      else if (tokenType == JavaTokenType.NE) {
        if (checkVariableUsage(referenceExpression, expression.getElseExpression(), expression.getThenExpression())) {
          registerError(referenceExpression, Boolean.FALSE);
        }
      }
    }

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
      if (!(condition instanceof PsiBinaryExpression binaryExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = extractVariableReference(binaryExpression);
      if (referenceExpression == null) {
        return;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (tokenType == JavaTokenType.EQEQ) {
        if (checkVariableUsage(referenceExpression, statement.getThenBranch(), statement.getElseBranch())) {
          registerError(referenceExpression, Boolean.TRUE);
        }
      }
      else if (tokenType == JavaTokenType.NE) {
        if (checkVariableUsage(referenceExpression, statement.getElseBranch(), statement.getThenBranch())) {
          registerError(referenceExpression, Boolean.TRUE);
        }
      }
    }

    private static boolean checkVariableUsage(PsiReferenceExpression referenceExpression, PsiElement thenContext, PsiElement elseContext) {
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable variable)) {
        return false;
      }
      if (thenContext != null && (contextExits(thenContext) || VariableAccessUtils.variableIsAssigned(variable, thenContext))) {
        return false;
      }
      if (elseContext == null || VariableAccessUtils.variableIsUsed(variable, elseContext)) {
        return false;
      }
      return true;
    }

    private static PsiReferenceExpression extractVariableReference(PsiBinaryExpression expression) {
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
      if (lhs == null) {
        return null;
      }
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
      if (rhs == null) {
        return null;
      }
      if (PsiTypes.nullType().equals(rhs.getType())) {
        if (!(lhs instanceof PsiReferenceExpression)) {
          return null;
        }
        return (PsiReferenceExpression)lhs;
      }
      if (PsiTypes.nullType().equals(lhs.getType())) {
        if (!(rhs instanceof PsiReferenceExpression)) {
          return null;
        }
        return (PsiReferenceExpression)rhs;
      }
      return null;
    }

    private static boolean contextExits(PsiElement context) {
      if (context instanceof PsiBlockStatement blockStatement) {
        final PsiStatement lastStatement = ControlFlowUtils.getLastStatementInBlock(blockStatement.getCodeBlock());
        return statementExits(lastStatement);
      }
      else {
        return statementExits(context);
      }
    }

    private static boolean statementExits(PsiElement context) {
      return context instanceof PsiReturnStatement || context instanceof PsiThrowStatement ||
             context instanceof PsiBreakStatement || context instanceof PsiContinueStatement;
    }
  }
}
