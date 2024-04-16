// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.increment;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

final class IncrementUtil {
  @Contract("null -> null")
  static @Nullable String getOperatorText(@Nullable PsiElement element) {
    if (element instanceof PsiUnaryExpression) {
      return ((PsiUnaryExpression)element).getOperationSign().getText();
    }
    return null;
  }

  @Contract("null -> null")
  static @Nullable PsiReferenceExpression getIncrementOrDecrementOperand(@Nullable PsiElement element) {
    if (element instanceof PsiUnaryExpression expression) {
      return getIncrementOrDecrementOperand(expression.getOperationTokenType(), expression.getOperand());
    }
    return null;
  }

  private static @Nullable PsiReferenceExpression getIncrementOrDecrementOperand(@Nullable IElementType tokenType, @Nullable PsiExpression operand) {
    final PsiExpression bareOperand = PsiUtil.skipParenthesizedExprDown(operand);
    if (bareOperand instanceof PsiReferenceExpression &&
        (JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType))) {
      return (PsiReferenceExpression)bareOperand;
    }
    return null;
  }
}
