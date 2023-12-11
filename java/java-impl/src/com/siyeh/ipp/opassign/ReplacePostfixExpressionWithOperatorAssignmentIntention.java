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
public final class ReplacePostfixExpressionWithOperatorAssignmentIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.postfix.expression.with.operator.assignment.intention.family.name");
  }

  @Override
  protected String getTextForElement(@NotNull PsiElement element) {
    final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)element;
    final PsiJavaToken sign = postfixExpression.getOperationSign();
    final IElementType tokenType = sign.getTokenType();
    final String replacementText;
    if (JavaTokenType.PLUSPLUS.equals(tokenType)) {
      replacementText = "+=";
    }
    else {
      replacementText = "-=";
    }
    final String signText = sign.getText();
    return CommonQuickFixBundle.message("fix.replace.x.with.y", signText, replacementText);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ReplacePostfixExpressionWithOperatorAssignmentPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)element;
    final PsiExpression operand = postfixExpression.getOperand();
    CommentTracker commentTracker = new CommentTracker();
    final String operandText = commentTracker.text(operand);
    final IElementType tokenType = postfixExpression.getOperationTokenType();
    if (JavaTokenType.PLUSPLUS.equals(tokenType)) {
      PsiReplacementUtil.replaceExpression(postfixExpression, operandText + "+=1", commentTracker);
    }
    else if (JavaTokenType.MINUSMINUS.equals(tokenType)) {
      PsiReplacementUtil.replaceExpression(postfixExpression, operandText + "-=1", commentTracker);
    }
  }
}