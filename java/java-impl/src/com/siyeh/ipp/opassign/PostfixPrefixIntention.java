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
public final class PostfixPrefixIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("postfix.prefix.intention.family.name");
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        final IElementType tokenType;
        if (element instanceof PsiPrefixExpression prefixExpression) {
          tokenType = prefixExpression.getOperationTokenType();
          if (prefixExpression.getOperand() == null) {
            return false;
          }
        }
        else if (element instanceof PsiPostfixExpression postfixExpression) {
          tokenType = postfixExpression.getOperationTokenType();
        }
        else {
          return false;
        }
        return JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType);
      }
    };
  }

  @Override
  protected String getTextForElement(@NotNull PsiElement element) {
    return CommonQuickFixBundle.message("fix.replace.with.x", getReplacementText(element));
  }

  @NotNull
  private static String getReplacementText(PsiElement element) {
    if (element instanceof PsiPrefixExpression prefixExpression) {
      final PsiExpression operand = prefixExpression.getOperand();
      assert operand != null;
      final PsiJavaToken sign = prefixExpression.getOperationSign();
      return operand.getText() + sign.getText();
    }
    else {
      final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)element;
      final PsiExpression operand = postfixExpression.getOperand();
      final PsiJavaToken sign = postfixExpression.getOperationSign();
      return sign.getText() + operand.getText();
    }
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiUnaryExpression expression = (PsiUnaryExpression)element;
    CommentTracker commentTracker = new CommentTracker();
    commentTracker.markUnchanged(expression.getOperand());
    PsiReplacementUtil.replaceExpression(expression, getReplacementText(element), commentTracker);
  }
}
