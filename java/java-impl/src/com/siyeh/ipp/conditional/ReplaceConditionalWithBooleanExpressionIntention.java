// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.conditional;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.*;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_BOOLEAN;
import static com.siyeh.ig.psiutils.ParenthesesUtils.AND_PRECEDENCE;

/**
 * @author Bas Leijdekkers
 */
public final class ReplaceConditionalWithBooleanExpressionIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.conditional.with.boolean.expression.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("replace.conditional.with.boolean.expression.intention.name");
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiConditionalExpression conditionalExpression)) {
          return false;
        }
        final PsiType type = conditionalExpression.getType();
        return PsiTypes.booleanType().equals(type) || type != null && type.equalsToText(JAVA_LANG_BOOLEAN);
      }
    };
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element;
    final PsiExpression condition = conditionalExpression.getCondition();
    CommentTracker tracker = new CommentTracker();
    final String replacementText = condition.getText() + "&&" + getText(conditionalExpression.getThenExpression(), tracker) + "||" +
                                   BoolUtils.getNegatedExpressionText(condition, AND_PRECEDENCE, tracker) + "&&" +
                                   getText(conditionalExpression.getElseExpression(), tracker);
    PsiReplacementUtil.replaceExpression((PsiExpression)element, replacementText, tracker);
  }

  private static String getText(@Nullable PsiExpression expression, CommentTracker tracker) {
    if (expression == null) {
      return "";
    }
    return tracker.text(expression, AND_PRECEDENCE);
  }
}
