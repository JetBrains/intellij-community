/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.shift;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.base.PsiElementPredicate;

class MultiplyByPowerOfTwoPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (element instanceof PsiBinaryExpression expression) {
      return binaryExpressionIsMultiplyByPowerOfTwo(expression);
    }
    else if (element instanceof PsiAssignmentExpression expression) {
      return assignmentExpressionIsMultiplyByPowerOfTwo(expression);
    }
    else {
      return false;
    }
  }

  private static boolean assignmentExpressionIsMultiplyByPowerOfTwo(PsiAssignmentExpression expression) {
    final IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.ASTERISKEQ) && !tokenType.equals(JavaTokenType.DIVEQ)) {
      return false;
    }
    final PsiExpression lhs = expression.getLExpression();
    final PsiType lhsType = lhs.getType();
    if (lhsType == null || !ShiftUtils.isIntegral(lhsType)) {
      return false;
    }
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getRExpression());
    return rhs != null && ShiftUtils.isPowerOfTwo(rhs);
  }

  private static boolean binaryExpressionIsMultiplyByPowerOfTwo(PsiBinaryExpression expression) {
    final IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.ASTERISK) && !tokenType.equals(JavaTokenType.DIV)) {
      return false;
    }
    final PsiExpression lhs = expression.getLOperand();
    final PsiType lhsType = lhs.getType();
    if (lhsType == null || !ShiftUtils.isIntegral(lhsType)) {
      return false;
    }
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
    return rhs != null && ShiftUtils.isPowerOfTwo(rhs);
  }
}