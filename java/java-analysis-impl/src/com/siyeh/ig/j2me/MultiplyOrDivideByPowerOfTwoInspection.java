/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class MultiplyOrDivideByPowerOfTwoInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean checkDivision = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("checkDivision", InspectionGadgetsBundle.message(
        "multiply.or.divide.by.power.of.two.divide.option")));
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("expression.can.be.replaced.problem.descriptor",
                                           calculateReplacementShift((PsiExpression)infos[0], new CommentTracker()));
  }

  static String calculateReplacementShift(PsiExpression expression, CommentTracker commentTracker) {
    final PsiExpression lhs;
    final PsiExpression rhs;
    final String operator;
    if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression exp = (PsiAssignmentExpression)expression;
      lhs = exp.getLExpression();
      rhs = exp.getRExpression();
      final IElementType tokenType = exp.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISKEQ)) {
        operator = "<<=";
      }
      else {
        operator = ">>=";
      }
    }
    else {
      final PsiBinaryExpression exp = (PsiBinaryExpression)expression;
      lhs = exp.getLOperand();
      rhs = exp.getROperand();
      final IElementType tokenType = exp.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISK)) {
        operator = "<<";
      }
      else {
        operator = ">>";
      }
    }

    if (!(rhs instanceof PsiLiteralExpression)) return null;

    final String lhsText = commentTracker.text(lhs, ParenthesesUtils.SHIFT_PRECEDENCE);
    String expString = lhsText + operator + ShiftUtils.getLogBaseTwo((PsiLiteralExpression)rhs);
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpression) {
      if (!(parent instanceof PsiParenthesizedExpression) &&
          ParenthesesUtils.getPrecedence((PsiExpression)parent) < ParenthesesUtils.SHIFT_PRECEDENCE) {
        expString = '(' + expString + ')';
      }
    }
    return expString;
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    if (expression instanceof PsiBinaryExpression binaryExpression) {
      final IElementType operationTokenType = binaryExpression.getOperationTokenType();
      if (JavaTokenType.DIV.equals(operationTokenType)) {
        return null;
      }
    }
    else if (expression instanceof PsiAssignmentExpression assignmentExpression) {
      final IElementType operationTokenType = assignmentExpression.getOperationTokenType();
      if (JavaTokenType.DIVEQ.equals(operationTokenType)) {
        return null;
      }
    }
    return new MultiplyByPowerOfTwoFix();
  }

  private static class MultiplyByPowerOfTwoFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "multiply.or.divide.by.power.of.two.replace.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiExpression expression = (PsiExpression)startElement;
      CommentTracker commentTracker = new CommentTracker();
      final String newExpression = calculateReplacementShift(expression, commentTracker);
      if (newExpression != null) {
        PsiReplacementUtil.replaceExpression(expression, newExpression, commentTracker);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantShiftVisitor();
  }

  private class ConstantShiftVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
      if (rhs == null) return;

      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISK) || (checkDivision && tokenType.equals(JavaTokenType.DIV))) {
        process(expression, rhs);
      }
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISKEQ) || (checkDivision && tokenType.equals(JavaTokenType.DIVEQ))) {
        process(expression, expression.getRExpression());
      }
    }

    private void process(PsiExpression anchor, PsiExpression rhs) {
      rhs = PsiUtil.skipParenthesizedExprDown(rhs);
      if (!ShiftUtils.isPowerOfTwo(rhs)) return;
      final PsiType type = anchor.getType();
      if (type == null || !ClassUtils.isIntegral(type)) return;
      registerError(anchor, anchor);
    }
  }
}