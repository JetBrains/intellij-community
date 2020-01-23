// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JavaPsiPatternUtil {
  /**
   * @param expression expression to search pattern variables in
   * @return list of pattern variables declared within an expression that could be visible outside of given expression.
   */
  @Contract(pure = true)
  public static @NotNull List<PsiPatternVariable> getPatternVariablesVisibleOutsideOf(@NotNull PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    boolean parentMayAccept =
      parent instanceof PsiPrefixExpression && ((PsiPrefixExpression)parent).getOperationTokenType().equals(JavaTokenType.EXCL) ||
      parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType().equals(JavaTokenType.ANDAND) ||
      parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType().equals(JavaTokenType.OROR) ||
      parent instanceof PsiConditionalExpression || parent instanceof PsiIfStatement || parent instanceof PsiConditionalLoopStatement;
    if (!parentMayAccept) {
      return Collections.emptyList();
    }
    List<PsiPatternVariable> list = new ArrayList<>();
    collectPatternVariableCandidates(expression, expression, list);
    return list;
  }

  private static void collectPatternVariableCandidates(@NotNull PsiExpression scope, @NotNull PsiExpression expression,
                                                       Collection<PsiPatternVariable> candidates) {
    while (true) {
      if (expression instanceof PsiParenthesizedExpression) {
        expression = ((PsiParenthesizedExpression)expression).getExpression();
      }
      else if (expression instanceof PsiPrefixExpression &&
               ((PsiPrefixExpression)expression).getOperationTokenType().equals(JavaTokenType.EXCL)) {
        expression = ((PsiPrefixExpression)expression).getOperand();
      }
      else {
        break;
      }
    }
    if (expression instanceof PsiInstanceOfExpression) {
      PsiPattern pattern = ((PsiInstanceOfExpression)expression).getPattern();
      if (pattern instanceof PsiTypeTestPattern) {
        PsiPatternVariable variable = ((PsiTypeTestPattern)pattern).getPatternVariable();
        if (variable != null && !PsiTreeUtil.isAncestor(scope, variable.getDeclarationScope(), false)) {
          candidates.add(variable);
        }
      }
    }
    if (expression instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          collectPatternVariableCandidates(scope, operand, candidates);
        }
      }
    }
  }
}
