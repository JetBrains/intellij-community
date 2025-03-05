// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public enum LoopDirection {
  ASCENDING,
  DESCENDING;

  /**
   * @param counter loop counter variable
   * @param updateStatement statement which changes the counter
   * @return {@link LoopDirection#ASCENDING} if counter increases in the loop e.g. for (int i = 0; i < 10; i++) {}<br>
   *         {@link LoopDirection#DESCENDING} if counter decreases in the loop e.g. for (int i = 10; i >= 0; i--) {}<br>
   *         null if current loop is uncountable
   */
  public static @Nullable LoopDirection evaluateLoopDirection(@NotNull PsiVariable counter, @Nullable PsiStatement updateStatement) {
    PsiExpressionStatement expressionStatement = tryCast(updateStatement, PsiExpressionStatement.class);
    if (expressionStatement == null) return null;
    PsiExpression expression = PsiUtil.skipParenthesizedExprDown(expressionStatement.getExpression());
    if (expression instanceof PsiUnaryExpression unaryExpression) {
      IElementType tokenType = unaryExpression.getOperationTokenType();
      if (tokenType != JavaTokenType.PLUSPLUS && tokenType != JavaTokenType.MINUSMINUS) return null;
      PsiExpression operand = unaryExpression.getOperand();
      if (!ExpressionUtils.isReferenceTo(operand, counter)) return null;
      return tokenType == JavaTokenType.PLUSPLUS ? ASCENDING : DESCENDING;
    }
    if (expression instanceof PsiAssignmentExpression assignmentExpression) {
      PsiExpression lhs = assignmentExpression.getLExpression();
      if (!ExpressionUtils.isReferenceTo(lhs, counter)) return null;
      PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getRExpression());
      IElementType tokenType = assignmentExpression.getOperationTokenType();
      if (tokenType == JavaTokenType.EQ) {
        PsiBinaryExpression binaryExpression = tryCast(rhs, PsiBinaryExpression.class);
        if (binaryExpression == null) return null;
        IElementType binaryTokenType = binaryExpression.getOperationTokenType();
        if (binaryTokenType != JavaTokenType.PLUS && binaryTokenType != JavaTokenType.MINUS) return null;
        PsiExpression lOperand = binaryExpression.getLOperand();
        PsiExpression rOperand = binaryExpression.getROperand();
        if (ExpressionUtils.isOne(lOperand)) {
          if (!ExpressionUtils.isReferenceTo(rOperand, counter)) return null;
          return binaryTokenType == JavaTokenType.PLUS ? ASCENDING : DESCENDING;
        }
        if (ExpressionUtils.isOne(rOperand)) {
          if (!ExpressionUtils.isReferenceTo(lOperand, counter)) return null;
          return binaryTokenType == JavaTokenType.PLUS ? ASCENDING : DESCENDING;
        }
      }
      else if (tokenType == JavaTokenType.PLUSEQ || tokenType == JavaTokenType.MINUSEQ) {
        if (ExpressionUtils.isOne(rhs)) {
          return tokenType == JavaTokenType.PLUSEQ ? ASCENDING : DESCENDING;
        }
      }
    }
    return null;
  }
}
