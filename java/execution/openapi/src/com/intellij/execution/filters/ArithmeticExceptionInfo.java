// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

public class ArithmeticExceptionInfo extends ExceptionInfo {
  ArithmeticExceptionInfo(int offset, String message) {
    super(offset, "java.lang.ArithmeticException", message);
  }

  @Override
  ExceptionLineRefiner.RefinerMatchResult matchSpecificExceptionElement(@NotNull PsiElement current) {
    PsiElement nextElement = PsiTreeUtil.nextVisibleLeaf(current);
    if (nextElement == null) return null;
    if (nextElement instanceof PsiJavaToken && (nextElement.textMatches("%") || nextElement.textMatches("/")) &&
        nextElement.getParent() instanceof PsiPolyadicExpression) {
      PsiExpression prevOperand = PsiTreeUtil.getPrevSiblingOfType(nextElement, PsiExpression.class);
      PsiExpression nextOperand = PsiUtil.skipParenthesizedExprDown(PsiTreeUtil.getNextSiblingOfType(nextElement, PsiExpression.class));
      if (prevOperand != null && TypeConversionUtil.isIntegralNumberType(prevOperand.getType()) &&
          nextOperand != null && TypeConversionUtil.isIntegralNumberType(nextOperand.getType())) {
        if(!PsiTreeUtil.isAncestor(prevOperand, current, false)) return null;
        while (nextOperand instanceof PsiUnaryExpression && ((PsiUnaryExpression)nextOperand).getOperationTokenType().equals(
          JavaTokenType.MINUS)) {
          nextOperand = PsiUtil.skipParenthesizedExprDown(((PsiUnaryExpression)nextOperand).getOperand());
        }
        if (nextOperand instanceof PsiLiteral) {
          Object value = ((PsiLiteral)nextOperand).getValue();
          if (value instanceof Number && ((Number)value).longValue() != 0) return null;
        }
        if (nextOperand == null) {
          return null;
        }
        return onTheSameLineFor(current, nextOperand, true);
      }
    }
    return null;
  }
}
