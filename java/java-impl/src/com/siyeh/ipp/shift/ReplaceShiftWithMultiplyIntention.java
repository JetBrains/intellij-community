/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public final class ReplaceShiftWithMultiplyIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.shift.with.multiply.intention.family.name");
  }

  @Override
  protected @NotNull String getTextForElement(@NotNull PsiElement element) {
    if (element instanceof PsiBinaryExpression) {
      final PsiBinaryExpression exp = (PsiBinaryExpression)element;
      final PsiJavaToken sign = exp.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      final String operatorString;
      if (tokenType.equals(JavaTokenType.LTLT)) {
        operatorString = "*";
      }
      else {
        operatorString = "/";
      }
      return CommonQuickFixBundle.message("fix.replace.x.with.y", sign.getText(), operatorString);
    }
    else {
      final PsiAssignmentExpression exp =
        (PsiAssignmentExpression)element;
      final PsiJavaToken sign = exp.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      final String assignString;
      if (JavaTokenType.LTLTEQ.equals(tokenType)) {
        assignString = "*=";
      }
      else {
        assignString = "/=";
      }
      return CommonQuickFixBundle.message("fix.replace.x.with.y", sign.getText(), assignString);
    }
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ShiftByLiteralPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    if (element instanceof PsiBinaryExpression) {
      replaceShiftWithMultiplyOrDivide(element);
    }
    else {
      replaceShiftAssignWithMultiplyOrDivideAssign(element);
    }
  }

  private static void replaceShiftAssignWithMultiplyOrDivideAssign(PsiElement element) {
    final PsiAssignmentExpression exp =
      (PsiAssignmentExpression)element;
    final PsiExpression lhs = exp.getLExpression();
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(exp.getRExpression());
    final IElementType tokenType = exp.getOperationTokenType();
    final String assignString;
    if (tokenType.equals(JavaTokenType.LTLTEQ)) {
      assignString = "*=";
    }
    else {
      assignString = "/=";
    }
    CommentTracker commentTracker = new CommentTracker();
    final String expString =
      commentTracker.text(lhs) + assignString + ShiftUtils.getExpBase2(rhs);
    PsiReplacementUtil.replaceExpression(exp, expString, commentTracker);
  }

  private static void replaceShiftWithMultiplyOrDivide(PsiElement element) {
    final PsiBinaryExpression exp = (PsiBinaryExpression)element;
    final PsiExpression lhs = exp.getLOperand();
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(exp.getROperand());
    final IElementType tokenType = exp.getOperationTokenType();
    final String operatorString;
    if (tokenType.equals(JavaTokenType.LTLT)) {
      operatorString = "*";
    }
    else {
      operatorString = "/";
    }
    CommentTracker commentTracker = new CommentTracker();
    final String lhsText = commentTracker.text(lhs, ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE);
    String expString = lhsText + operatorString + ShiftUtils.getExpBase2(rhs);
    final PsiElement parent = exp.getParent();
    if (parent instanceof PsiExpression) {
      if (!(parent instanceof PsiParenthesizedExpression) &&
          ParenthesesUtils.getPrecedence((PsiExpression)parent) < ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE) {
        expString = '(' + expString + ')';
      }
    }

    PsiReplacementUtil.replaceExpression(exp, expString, commentTracker);
  }
}