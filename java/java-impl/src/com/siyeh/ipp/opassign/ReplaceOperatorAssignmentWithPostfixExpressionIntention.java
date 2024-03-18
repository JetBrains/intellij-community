// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.opassign;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class ReplaceOperatorAssignmentWithPostfixExpressionIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.operator.assignment.with.postfix.expression.intention.family.name");
  }

  @Override
  protected String getTextForElement(@NotNull PsiElement element) {
    final PsiAssignmentExpression assignment = (PsiAssignmentExpression)element;
    final PsiJavaToken sign = assignment.getOperationSign();
    final IElementType tokenType = sign.getTokenType();
    return CommonQuickFixBundle.message("fix.replace.x.with.y", sign.getText(), JavaTokenType.PLUSEQ.equals(tokenType) ? "++" : "--");
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ReplaceOperatorAssignmentWithPostfixExpressionPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiAssignmentExpression assignment = (PsiAssignmentExpression)element;
    final PsiExpression expression = assignment.getLExpression();
    CommentTracker commentTracker = new CommentTracker();
    final String expressionText = commentTracker.text(expression);
    final IElementType tokenType = assignment.getOperationTokenType();
    final String newExpressionText;
    if (JavaTokenType.PLUSEQ.equals(tokenType)) {
      newExpressionText = expressionText + "++";
    }
    else if (JavaTokenType.MINUSEQ.equals(tokenType)) {
      newExpressionText = expressionText + "--";
    }
    else {
      return;
    }
    PsiReplacementUtil.replaceExpression(assignment, newExpressionText, commentTracker);
  }
}