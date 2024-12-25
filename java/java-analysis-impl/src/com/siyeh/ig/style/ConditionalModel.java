// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A model which represents conditional
 */
public class ConditionalModel {
  private final @NotNull PsiExpression myCondition;
  private final @NotNull PsiExpression myThenExpression;
  private final @NotNull PsiExpression myElseExpression;
  private final @NotNull PsiType myType;

  ConditionalModel(@NotNull PsiExpression condition,
                          @NotNull PsiExpression thenExpression,
                          @NotNull PsiExpression elseExpression,
                          @NotNull PsiType type) {
    myCondition = condition;
    myThenExpression = thenExpression;
    myElseExpression = elseExpression;
    myType = type;
  }

  /**
   * Condition part of the conditional.
   *
   * @return condition
   */
  public @NotNull PsiExpression getCondition() {
    return myCondition;
  }

  /**
   * Expression from then branch.
   *
   * @return then expression
   */
  public @NotNull PsiExpression getThenExpression() {
    return myThenExpression;
  }

  /**
   * Expression from else branch or expression that follows conditional and can be used as else branch.
   *
   * @return else expression
   */
  public @NotNull PsiExpression getElseExpression() {
    return myElseExpression;
  }

  /**
   * Conditional result type.
   * In case when conditional is an if statement and the type can not be deduced from context the type is determined from types of
   * both branches, essentially interpreting if statement like 'if (cond) then_expr else else_expr' as conditional expression
   * 'cond ? then_expr : else_expr'.
   *
   * @return result type
   * @see ConditionalModel#getType(PsiExpression, PsiExpression, PsiExpression)
   */
  public @NotNull PsiType getType() {
    return myType;
  }

  /**
   * Convert conditional expression to model.
   * Conversion is possible only when expression is complete and branches types are assignable from one to other or have a common ancestor.
   *
   * @param conditional conditional expression
   * @return null if conditional can't be converted, model otherwise
   */
  public static @Nullable ConditionalModel from(@NotNull PsiConditionalExpression conditional) {
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(conditional.getCondition());
    if (condition == null) return null;
    PsiExpression thenExpression = conditional.getThenExpression();
    if (thenExpression == null) return null;
    PsiExpression elseExpression = conditional.getElseExpression();
    if (elseExpression == null) return null;
    PsiType type = getType(condition, thenExpression, elseExpression);
    if (type == null) return null;
    return new ConditionalModel(condition, thenExpression, elseExpression, type);
  }

  static @Nullable PsiType getType(@NotNull PsiExpression condition,
                                   @NotNull PsiExpression thenExpression,
                                   @NotNull PsiExpression elseExpression) {
    final PsiType thenType = thenExpression.getType();
    final PsiType elseType = elseExpression.getType();
    if (thenType == null || elseType == null) return null;
    if (thenType.isAssignableFrom(elseType)) return thenType;
    if (elseType.isAssignableFrom(thenType)) return elseType;
    if (!(thenType instanceof PsiClassType) || !(elseType instanceof PsiClassType)) return null;
    if (TypeConversionUtil.isPrimitiveWrapper(thenType) || TypeConversionUtil.isPrimitiveWrapper(elseType)) return null;
    return GenericsUtil.getLeastUpperBound(thenType, elseType, condition.getManager());
  }
}
