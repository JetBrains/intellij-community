// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.redundantCast;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;

public final class RemoveRedundantCastUtil {
  private static final Logger LOG = Logger.getInstance(RemoveRedundantCastUtil.class);

  public static PsiExpression removeCast(PsiTypeCastExpression castExpression) {
    if (castExpression == null) return null;
    PsiElement parent = castExpression.getParent();
    PsiExpression operand = castExpression.getOperand();
    if (operand instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parExpr = (PsiParenthesizedExpression)operand;
      PsiElement topParent = PsiUtil.skipParenthesizedExprUp(parent);
      if (!(topParent instanceof PsiExpression) ||
          !PsiPrecedenceUtil.areParenthesesNeeded(parExpr.getExpression(), (PsiExpression)topParent, true)) {
        operand = parExpr.getExpression();
      }
    }
    if (operand == null) return null;

    PsiExpression toBeReplaced = castExpression;

    while (parent instanceof PsiParenthesizedExpression) {
      toBeReplaced = (PsiExpression)parent;
      parent = parent.getParent();
    }

    try {
      return (PsiExpression)new CommentTracker().replaceAndRestoreComments(toBeReplaced, operand);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return toBeReplaced;
  }
}
