// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.bool;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.*;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class NegateConditionalIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("negate.conditional.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("negate.conditional.intention.name");
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element;
    PsiExpression condition = conditionalExpression.getCondition();
    PsiExpression thenExpression = conditionalExpression.getThenExpression();
    PsiExpression elseExpression = conditionalExpression.getElseExpression();
    CommentTracker tracker = new CommentTracker();
    final String newExpression = tracker.text(condition) + '?' +
                                 BoolUtils.getNegatedExpressionText(thenExpression, tracker) + ':' +
                                 BoolUtils.getNegatedExpressionText(elseExpression, tracker);
    PsiReplacementUtil.replaceExpressionWithNegatedExpression(conditionalExpression, newExpression, tracker);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new BooleanConditionalExpressionPredicate();
  }

  private static class BooleanConditionalExpressionPredicate implements PsiElementPredicate {

    @Override
    public boolean satisfiedBy(PsiElement element) {
      if (!(element instanceof PsiConditionalExpression conditionalExpression)) {
        return false;
      }
      final PsiType type = conditionalExpression.getType();
      return PsiTypes.booleanType().equals(type);
    }
  }
}
