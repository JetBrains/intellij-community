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
import com.intellij.psi.tree.IElementType;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public final class FlipComparisonIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("flip.comparison.intention.family.name");
  }

  @Override
  public @NotNull String getTextForElement(@NotNull PsiElement element) {
    final PsiBinaryExpression expression = (PsiBinaryExpression)element;
    final PsiJavaToken sign = expression.getOperationSign();
    String operatorText = sign.getText();
    String flippedOperatorText = ComparisonUtils.getFlippedComparison(sign.getTokenType());
    if (operatorText.equals(flippedOperatorText)) {
      return IntentionPowerPackBundle.message("flip.smth.intention.name",
                                              operatorText);
    }
    else {
      return IntentionPowerPackBundle.message(
        "flip.comparison.intention.name",
        operatorText, flippedOperatorText);
    }
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new FlipComparisonPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiBinaryExpression expression =
      (PsiBinaryExpression)element;
    final PsiExpression lhs = expression.getLOperand();
    final PsiExpression rhs = expression.getROperand();
    final IElementType tokenType = expression.getOperationTokenType();
    assert rhs != null;
    CommentTracker commentTracker = new CommentTracker();
    final String expString = commentTracker.text(rhs) +
                             ComparisonUtils.getFlippedComparison(tokenType) +
                             commentTracker.text(lhs);
    PsiReplacementUtil.replaceExpression(expression, expString, commentTracker);
  }
}