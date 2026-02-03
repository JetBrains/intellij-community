// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Ordered binary expression for commutative operations.
 *
 * @param <F> First operand
 * @param <S> Second operand
 */
public class OrderedBinaryExpression<F extends PsiExpression, S extends PsiExpression> {
  private static final List<IElementType> COMMUTATIVE_OPERATORS = List.of(JavaTokenType.AND,
                                                                          JavaTokenType.ANDAND,
                                                                          JavaTokenType.OROR,
                                                                          JavaTokenType.EQEQ,
                                                                          JavaTokenType.NE,
                                                                          JavaTokenType.OR,
                                                                          JavaTokenType.PLUS,
                                                                          JavaTokenType.ASTERISK,
                                                                          JavaTokenType.XOR);
  private final @NotNull F firstOperand;
  private final @NotNull IElementType tokenType;
  private final @NotNull S secondOperand;

  private OrderedBinaryExpression(@NotNull F firstOperand, @NotNull IElementType tokenType, @NotNull S secondOperand) {
    this.firstOperand = firstOperand;
    this.tokenType = tokenType;
    this.secondOperand = secondOperand;
  }

  public @NotNull F getFirstOperand() {
    return firstOperand;
  }

  public @NotNull IElementType getTokenType() {
    return tokenType;
  }

  public @NotNull S getSecondOperand() {
    return secondOperand;
  }

  /**
   * Return the items of a binary expression in the order it is specified. It reverses the operator if needed.
   *
   * @param <O>        the required expression type
   * @param node       the supposed binary expression
   * @param firstClass the class representing the required expression type
   * @return the items of a binary expression in the order it is specified. It reverses the operator if needed.
   */
  public static @Nullable <O extends PsiExpression> OrderedBinaryExpression<O, PsiExpression> from(final @Nullable PsiExpression node,
                                                                                                   final @Nullable Class<O> firstClass) {
    PsiBinaryExpression expression = as(node, PsiBinaryExpression.class);
    if (expression == null || firstClass == null) return null;
    O leftOperand = as(expression.getLOperand(), firstClass);
    O rightOperand = as(expression.getROperand(), firstClass);
    PsiExpression unparenthesizedRightOperand = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
    if (leftOperand != null && rightOperand == null && unparenthesizedRightOperand != null) {
      return new OrderedBinaryExpression<>(leftOperand, expression.getOperationTokenType(), unparenthesizedRightOperand);
    }
    if (leftOperand == null && rightOperand != null) {
      IElementType mirroredOperator = mirrorOperator(expression);
      PsiExpression unparenthesizedLeftOperand = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
      if (mirroredOperator != null && unparenthesizedLeftOperand != null) {
        return new OrderedBinaryExpression<>(rightOperand, mirroredOperator, unparenthesizedLeftOperand);
      }
    }
    return null;
  }

  /**
   * Return the items of a binary expression in the order it is specified. It reverses the operator if needed.
   *
   * @param <F>         the required expression type
   * @param <S>         the required expression type
   * @param node        the supposed binary expression
   * @param firstClass  the class representing the required expression type
   * @param secondClass the class representing the required expression type
   * @return the items of a binary expression in the order it is specified. It reverses the operator if needed.
   */
  public static @Nullable <F extends PsiExpression, S extends PsiExpression> OrderedBinaryExpression<F, S> from(final @Nullable PsiExpression node,
                                                                                                                final @Nullable Class<F> firstClass,
                                                                                                                final @Nullable Class<S> secondClass) {
    PsiBinaryExpression expression = as(node, PsiBinaryExpression.class);
    if (expression == null || firstClass == null || secondClass == null) return null;
    F leftFirst = as(expression.getLOperand(), firstClass);
    S rightSecond = as(expression.getROperand(), secondClass);
    if (leftFirst != null && rightSecond != null) {
      return new OrderedBinaryExpression<>(leftFirst, expression.getOperationTokenType(), rightSecond);
    }
    IElementType mirroredOperator = mirrorOperator(expression);
    if (mirroredOperator != null) {
      F rightFirst = as(expression.getROperand(), firstClass);
      S leftSecond = as(expression.getLOperand(), secondClass);

      if (rightFirst != null && leftSecond != null) {
        return new OrderedBinaryExpression<>(rightFirst, mirroredOperator, leftSecond);
      }
    }
    return null;
  }

  private static <T extends PsiExpression> T as(PsiExpression input, Class<T> aClass) {
    return tryCast(PsiUtil.skipParenthesizedExprDown(input), aClass);
  }

  private static IElementType mirrorOperator(final PsiBinaryExpression binaryExpression) {
    IElementType operationTokenType = binaryExpression.getOperationTokenType();
    if (COMMUTATIVE_OPERATORS.contains(operationTokenType)) {
      return operationTokenType;
    }
    else if (JavaTokenType.GT.equals(operationTokenType)) {
      return JavaTokenType.LT;
    }
    else if (JavaTokenType.GE.equals(operationTokenType)) {
      return JavaTokenType.LE;
    }
    else if (JavaTokenType.LT.equals(operationTokenType)) {
      return JavaTokenType.GT;
    }
    else if (JavaTokenType.LE.equals(operationTokenType)) {
      return JavaTokenType.GE;
    }
    return null;
  }
}
