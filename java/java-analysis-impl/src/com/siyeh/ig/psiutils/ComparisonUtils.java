/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class ComparisonUtils {

  private ComparisonUtils() {}

  private static final @NotNull TokenSet s_comparisonTokens = TokenSet.create(JavaTokenType.EQEQ,
                                                                              JavaTokenType.NE,
                                                                              JavaTokenType.GT,
                                                                              JavaTokenType.LT,
                                                                              JavaTokenType.GE,
                                                                              JavaTokenType.LE);

  private static final Map<IElementType, String> s_swappedComparisons = new HashMap<>(6);

  private static final Map<IElementType, String> s_invertedComparisons = new HashMap<>(6);

  static {
    s_swappedComparisons.put(JavaTokenType.EQEQ, "==");
    s_swappedComparisons.put(JavaTokenType.NE, "!=");
    s_swappedComparisons.put(JavaTokenType.GT, "<");
    s_swappedComparisons.put(JavaTokenType.LT, ">");
    s_swappedComparisons.put(JavaTokenType.GE, "<=");
    s_swappedComparisons.put(JavaTokenType.LE, ">=");

    s_invertedComparisons.put(JavaTokenType.EQEQ, "!=");
    s_invertedComparisons.put(JavaTokenType.NE, "==");
    s_invertedComparisons.put(JavaTokenType.GT, "<=");
    s_invertedComparisons.put(JavaTokenType.LT, ">=");
    s_invertedComparisons.put(JavaTokenType.GE, "<");
    s_invertedComparisons.put(JavaTokenType.LE, ">");
  }

  public static boolean isComparison(@Nullable PsiExpression expression) {
    if (!(expression instanceof PsiPolyadicExpression polyadicExpression)) {
      return false;
    }
    final IElementType tokenType = polyadicExpression.getOperationTokenType();
    return isComparisonOperation(tokenType);
  }

  public static boolean isComparisonOperation(IElementType tokenType) {
    return s_comparisonTokens.contains(tokenType);
  }

  public static String getFlippedComparison(IElementType tokenType) {
    return s_swappedComparisons.get(tokenType);
  }

  /**
   * @param expression the expression to check
   * @return true, when this is an expression of the form {@code a == b} or {@code a != b}, false otherwise.
   */
  public static boolean isEqualityComparison(@NotNull PsiExpression expression) {
    if (!(expression instanceof PsiPolyadicExpression polyadicExpression)) {
      return false;
    }
    final IElementType tokenType = polyadicExpression.getOperationTokenType();
    return tokenType.equals(JavaTokenType.EQEQ) || tokenType.equals(JavaTokenType.NE);
  }

  public static String getNegatedComparison(IElementType tokenType) {
    return s_invertedComparisons.get(tokenType);
  }

  @Contract("null, _, _ -> false")
  public static boolean isNullComparison(PsiExpression expression, @NotNull PsiVariable variable, boolean equal) {
    return variable.equals(ExpressionUtils.getVariableFromNullComparison(expression, equal));
  }

  @Contract("null -> false")
  public static boolean isNullComparison(PsiExpression expression) {
    return expression instanceof PsiBinaryExpression &&
           ExpressionUtils.getValueComparedWithNull((PsiBinaryExpression)expression) != null;
  }
}
