// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.ExtractParameterAsLocalVariableFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class AssignmentToForLoopParameterInspection extends BaseInspection {

  /**
   * @noinspection PublicField for externalization purposes
   */
  public boolean m_checkForeachParameters = true;

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final Boolean foreachLoop = (Boolean)infos[0];
    if (!foreachLoop.booleanValue()) {
      return null;
    }
    return new ExtractParameterAsLocalVariableFix();
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assignment.to.for.loop.parameter.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_checkForeachParameters", InspectionGadgetsBundle.message("assignment.to.for.loop.parameter.check.foreach.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentToForLoopParameterVisitor();
  }

  private class AssignmentToForLoopParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (expression.getRExpression() == null) {
        return;
      }
      final PsiExpression lhs = expression.getLExpression();
      checkForForLoopParam(lhs);
      checkForForeachLoopParam(lhs);
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
          !tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      checkForForLoopParam(operand);
      checkForForeachLoopParam(operand);  //sensible due to autoboxing/unboxing
    }

    private void checkForForLoopParam(PsiExpression expression) {
      final PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(expression);
      if (variable == null) {
        return;
      }
      final PsiElement variableParent = variable.getParent();
      if (!(variableParent instanceof PsiDeclarationStatement declarationStatement)) {
        return;
      }
      final PsiElement parent = declarationStatement.getParent();
      if (!(parent instanceof PsiForStatement forStatement)) {
        return;
      }
      final PsiStatement initialization = forStatement.getInitialization();
      if (initialization == null ||
          !initialization.equals(declarationStatement) ||
          !isInForStatementBody(expression, forStatement) ||
          forStatement.getUpdate() == null) {
        return;
      }
      registerError(expression, Boolean.FALSE);
    }

    private void checkForForeachLoopParam(PsiExpression expression) {
      if (!m_checkForeachParameters) {
        return;
      }
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (!(expression instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      if (referenceExpression.getQualifierExpression() != null) {
        return;
      }
      PsiElement element = referenceExpression.resolve();
      if (!(element instanceof PsiParameter) || !(element.getParent() instanceof PsiForeachStatement)) {
        return;
      }
      registerError(expression, Boolean.TRUE);
    }

    private static boolean isInForStatementBody(PsiExpression expression, PsiForStatement statement) {
      return PsiTreeUtil.isAncestor(statement.getBody(), expression, true);
    }
  }
}
