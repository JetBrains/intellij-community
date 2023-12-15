/*
 * Copyright 2007-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.expression;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.tree.IElementType;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public final class FlipExpressionIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("flip.expression.intention.family.name");
  }

  @Override
  public @NotNull String getTextForElement(@NotNull PsiElement element) {
    final PsiPolyadicExpression expression = (PsiPolyadicExpression)element.getParent();
    final PsiExpression[] operands = expression.getOperands();
    final PsiJavaToken sign = expression.getTokenBeforeOperand(operands[1]);
    final String operatorText = sign == null ? "" : sign.getText();
    final IElementType tokenType = expression.getOperationTokenType();
    final boolean commutative = ParenthesesUtils.isCommutativeOperator(tokenType);
    if (commutative && !ExpressionUtils.isStringConcatenation(expression)) {
      return IntentionPowerPackBundle.message("flip.smth.intention.name", operatorText);
    }
    else {
      return IntentionPowerPackBundle.message("flip.smth.intention.name1", operatorText);
    }
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ExpressionPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final PsiJavaToken token = (PsiJavaToken)element;
    int offset = context.offset();
    final PsiElement parent = token.getParent();
    if (!(parent instanceof PsiPolyadicExpression polyadicExpression)) {
      return;
    }
    final PsiExpression[] operands = polyadicExpression.getOperands();
    final StringBuilder newExpression = new StringBuilder();
    CommentTracker commentTracker = new CommentTracker();
    String prevOperand = null;
    final String tokenText = token.getText() + ' '; // 2- -1 without the space is not legal
    for (PsiExpression operand : operands) {
      final PsiJavaToken token1 = polyadicExpression.getTokenBeforeOperand(operand);
      if (token == token1) {
        newExpression.append(commentTracker.text(operand)).append(tokenText);
        continue;
      }
      if (prevOperand != null) {
        newExpression.append(prevOperand).append(tokenText);
      }
      prevOperand = commentTracker.text(operand);
    }
    newExpression.append(prevOperand);

    PsiReplacementUtil.replaceExpression(polyadicExpression, newExpression.toString(), commentTracker);
    updater.moveCaretTo(offset);
  }
}