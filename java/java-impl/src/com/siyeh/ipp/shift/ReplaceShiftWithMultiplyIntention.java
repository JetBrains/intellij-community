/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.shift;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public final class ReplaceShiftWithMultiplyIntention extends MCIntention implements DumbAware {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.shift.with.multiply.intention.family.name");
  }

  @Override
  protected @NotNull String getTextForElement(@NotNull PsiElement element) {
    if (element instanceof PsiBinaryExpression exp) {
      final PsiJavaToken sign = exp.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      final String operatorString = tokenType.equals(JavaTokenType.LTLT) ? "*" : "/";
      return CommonQuickFixBundle.message("fix.replace.x.with.y", sign.getText(), operatorString);
    }
    else {
      final PsiAssignmentExpression exp = (PsiAssignmentExpression)element;
      final PsiJavaToken sign = exp.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      final String assignString = JavaTokenType.LTLTEQ.equals(tokenType) ? "*=" : "/=";
      return CommonQuickFixBundle.message("fix.replace.x.with.y", sign.getText(), assignString);
    }
  }

  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new ShiftByLiteralPredicate();
  }

  @Override
  public void invoke(@NotNull PsiElement element) {
    if (element instanceof PsiBinaryExpression) {
      replaceShiftWithMultiplyOrDivide(element);
    }
    else {
      replaceShiftAssignWithMultiplyOrDivideAssign(element);
    }
  }

  private static void replaceShiftAssignWithMultiplyOrDivideAssign(PsiElement element) {
    final PsiAssignmentExpression exp = (PsiAssignmentExpression)element;
    final PsiExpression lhs = exp.getLExpression();
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(exp.getRExpression());
    final IElementType tokenType = exp.getOperationTokenType();
    final String assignString = tokenType.equals(JavaTokenType.LTLTEQ) ? "*=" : "/=";
    final PsiLiteralExpression literal = (PsiLiteralExpression)rhs;
    assert rhs != null;
    final Number value = (Number)literal.getValue();
    assert value != null;
    CommentTracker commentTracker = new CommentTracker();
    final String expString = PsiTypes.longType().equals(lhs.getType())
                             ? commentTracker.text(lhs) + assignString + (1L << value.intValue()) + 'L'
                             : commentTracker.text(lhs) + assignString + (1 << value.intValue());
    PsiReplacementUtil.replaceExpression(exp, expString, commentTracker);
  }

  private static void replaceShiftWithMultiplyOrDivide(PsiElement element) {
    final PsiBinaryExpression expression = (PsiBinaryExpression)element;
    final PsiExpression lhs = expression.getLOperand();
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
    final IElementType tokenType = expression.getOperationTokenType();
    final String operatorString = tokenType.equals(JavaTokenType.LTLT) ? "*" : "/";
    CommentTracker commentTracker = new CommentTracker();
    final String lhsText = commentTracker.text(lhs, ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE);
    final PsiLiteralExpression literal = (PsiLiteralExpression)rhs;
    assert rhs != null;
    final Number value = (Number)literal.getValue();
    assert value != null;
    String expString = PsiTypes.longType().equals(expression.getType())
                       ? lhsText + operatorString + (1L << value.intValue()) + 'L'
                       : lhsText + operatorString + (1 << value.intValue());
    if (expression.getParent() instanceof PsiExpression parent && !(parent instanceof PsiParenthesizedExpression) &&
        ParenthesesUtils.getPrecedence(parent) < ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE) {
      expString = '(' + expString + ')';
    }

    PsiReplacementUtil.replaceExpression(expression, expString, commentTracker);
  }
}