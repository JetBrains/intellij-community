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
package com.siyeh.ipp.bool;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public final class NegateComparisonIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("negate.comparison.intention.family.name");
  }

  @Override
  public String getTextForElement(@NotNull PsiElement element) {
    final PsiBinaryExpression exp = (PsiBinaryExpression)element;
    final PsiJavaToken sign = exp.getOperationSign();
    String operatorText = sign.getText();
    String negatedOperatorText = ComparisonUtils.getNegatedComparison(sign.getTokenType());
    if (operatorText.equals(negatedOperatorText)) {
      return IntentionPowerPackBundle.message(
        "negate.comparison.intention.name", operatorText);
    }
    else {
      return IntentionPowerPackBundle.message(
        "negate.comparison.intention.name1", operatorText,
        negatedOperatorText);
    }
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ComparisonPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiBinaryExpression expression = (PsiBinaryExpression)element;
    final PsiExpression lhs = expression.getLOperand();
    final PsiExpression rhs = expression.getROperand();
    final String negatedOperator = ComparisonUtils.getNegatedComparison(expression.getOperationTokenType());
    final String lhsText = lhs.getText();
    assert rhs != null;
    final String rhsText = rhs.getText();
    CommentTracker tracker = new CommentTracker();
    tracker.markUnchanged(lhs);
    tracker.markUnchanged(rhs);
    PsiReplacementUtil.replaceExpressionWithNegatedExpression(expression, lhsText + negatedOperator + rhsText, tracker);
  }
}