// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class MergeCallSequenceToChainIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("merge.call.sequence.to.chain.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("merge.call.sequence.to.chain.intention.name");
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new CallSequencePredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    if (!(element instanceof PsiExpressionStatement statement)) {
      return;
    }
    final PsiExpressionStatement nextSibling = PsiTreeUtil.getNextSiblingOfType(statement, PsiExpressionStatement.class);
    if (nextSibling == null) {
      return;
    }
    final PsiExpression expression = statement.getExpression();
    final StringBuilder newMethodCallExpression = new StringBuilder(expression.getText());
    final PsiExpression expression1 = nextSibling.getExpression();
    if (!(expression1 instanceof PsiMethodCallExpression)) {
      return;
    }
    PsiMethodCallExpression methodCallExpression = getRootMethodCallExpression((PsiMethodCallExpression)expression1);
    CommentTracker tracker = new CommentTracker();
    while (true) {
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      tracker.markUnchanged(argumentList);
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      newMethodCallExpression.append('.').append(methodName).append(argumentList.getText());
      final PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodCallExpression.getParent());
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        break;
      }
      methodCallExpression = (PsiMethodCallExpression)grandParent;
    }
    PsiReplacementUtil.replaceExpression(expression, newMethodCallExpression.toString());
    tracker.deleteAndRestoreComments(nextSibling);
  }

  private static PsiMethodCallExpression getRootMethodCallExpression(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());
    if (qualifierExpression instanceof PsiMethodCallExpression methodCallExpression) {
      return getRootMethodCallExpression(methodCallExpression);
    }
    return expression;
  }
}
