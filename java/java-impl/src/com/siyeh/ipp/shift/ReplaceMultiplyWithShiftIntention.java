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

public final class ReplaceMultiplyWithShiftIntention extends MCIntention implements DumbAware {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.multiply.with.shift.intention.family.name");
  }

  @Override
  protected String getTextForElement(@NotNull PsiElement element) {
    if (element instanceof PsiBinaryExpression exp) {
      final PsiJavaToken sign = exp.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      final String operatorString = tokenType.equals(JavaTokenType.ASTERISK) ? "<<" : ">>";
      return CommonQuickFixBundle.message("fix.replace.x.with.y", sign.getText(), operatorString);
    }
    else {
      final PsiAssignmentExpression exp = (PsiAssignmentExpression)element;
      final PsiJavaToken sign = exp.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      final String assignString = tokenType.equals(JavaTokenType.ASTERISKEQ) ? "<<=" : ">>=";
      return CommonQuickFixBundle.message("fix.replace.x.with.y", sign.getText(), assignString);
    }
  }

  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new MultiplyByPowerOfTwoPredicate();
  }

  @Override
  public void invoke(@NotNull PsiElement element) {
    if (element instanceof PsiBinaryExpression) {
      replaceMultiplyOrDivideWithShift((PsiBinaryExpression)element);
    }
    else {
      replaceMultiplyOrDivideAssignWithShiftAssign((PsiAssignmentExpression)element);
    }
  }

  private static void replaceMultiplyOrDivideAssignWithShiftAssign(PsiAssignmentExpression expression) {
    final PsiExpression lhs = expression.getLExpression();
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getRExpression());
    final IElementType tokenType = expression.getOperationTokenType();
    final String assignString = tokenType.equals(JavaTokenType.ASTERISKEQ) ? "<<=" : ">>=";
    CommentTracker commentTracker = new CommentTracker();
    final String expString = commentTracker.text(lhs) + assignString + ShiftUtils.getLogBase2(rhs);
    PsiReplacementUtil.replaceExpression(expression, expString, commentTracker);
  }

  private static void replaceMultiplyOrDivideWithShift(PsiBinaryExpression expression) {
    final PsiExpression lhs = expression.getLOperand();
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
    final IElementType tokenType = expression.getOperationTokenType();
    final String operatorString = tokenType.equals(JavaTokenType.ASTERISK) ? "<<" : ">>";
    final String lhsText = PsiTypes.intType().equals(lhs.getType()) && PsiTypes.longType().equals(expression.getType())
                           ? "((long)" + lhs.getText() + ')' : lhs.getText();
    String expString = lhsText + operatorString + ShiftUtils.getLogBase2(rhs);
    if (expression.getParent() instanceof PsiExpression parent && !(parent instanceof PsiParenthesizedExpression) &&
        ParenthesesUtils.getPrecedence(parent) < ParenthesesUtils.SHIFT_PRECEDENCE) {
      expString = '(' + expString + ')';
    }
    CommentTracker commentTracker = new CommentTracker();
    commentTracker.markUnchanged(lhs);
    PsiReplacementUtil.replaceExpression(expression, expString, commentTracker);
  }
}