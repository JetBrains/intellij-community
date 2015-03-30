/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.psi.impl.JavaConstantExpressionEvaluator.computeConstantExpression;
import static java.lang.Integer.parseInt;

public class ComparisonFoldingManager {
  public static final String COMPARISON_GROUP_NAME = "comparison";

  private static final String EQUALS_METHOD_NAME = "equals";
  private static final String SIGNUM_METHOD_NAME = "signum";
  private static final String COMPARE_TO_METHOD_NAME = "compareTo";

  private static final String EMPTY_PLACEHOLDER = "";

  private static final Logger LOG = Logger.getInstance("#" + ComparisonFoldingManager.class.getName());

  private static final Map<IElementType, String> OPERATOR_REPLACEMENTS = ContainerUtil.newHashMap(
    Pair.create(JavaTokenType.LT, "<"),
    Pair.create(JavaTokenType.LE, "≤"),

    Pair.create(JavaTokenType.EQEQ, "≡"),
    Pair.create(JavaTokenType.NE, "≢"),

    Pair.create(JavaTokenType.GT, ">"),
    Pair.create(JavaTokenType.GE, "≥")
  );

  @SuppressWarnings("ConstantConditions")
  public static @NotNull Collection<NamedFoldingDescriptor> fold(@NotNull final PsiBinaryExpression expression) {
    try {
      final PsiJavaToken operationSign = expression.getOperationSign();
      if (operationSign.getText().length() != 1) {
        final int rangeStart = expression.getLOperand().getTextRange().getEndOffset();
        final int rangeEnd = expression.getROperand().getTextRange().getStartOffset();

        final FoldingGroup group = FoldingGroup.newGroup(COMPARISON_GROUP_NAME);
        final NamedFoldingDescriptor operator = new NamedFoldingDescriptor(expression,
                                                                           rangeStart, rangeEnd, group,
                                                                           getPlaceholderOperator(operationSign.getTokenType()));
        return Collections.singletonList(operator);
      }
    }
    catch (final RuntimeException e) {
      LOG.warn(e);
    }
    return ContainerUtil.emptyList();
  }

  public static @NotNull Collection<NamedFoldingDescriptor> fold(@NotNull final PsiMethodCallExpression expression) {
    try {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();

      if (EQUALS_METHOD_NAME.equals(methodName)) {
        return getEqualsFolds(expression, methodExpression);
      }
      else if (SIGNUM_METHOD_NAME.equals(methodName)) {
        return getSignumFolds(expression, methodExpression, getBinaryParent(expression));
      }
      else if (COMPARE_TO_METHOD_NAME.equals(methodName)) {
        return getCompareToFolds(expression, methodExpression, getBinaryParent(expression));
      }
    }
    catch (final RuntimeException e) {
      LOG.warn(e);
    }
    return ContainerUtil.emptyList();
  }

  private static @NotNull Collection<NamedFoldingDescriptor> getEqualsFolds(@NotNull final PsiMethodCallExpression expression,
                                                                            @NotNull final PsiReferenceExpression methodExpression) {
    final List<NamedFoldingDescriptor> results = ContainerUtil.newArrayList();

    final FoldingGroup group = FoldingGroup.newGroup(COMPARISON_GROUP_NAME);

    final TextRange lhsRange = getCallerRange(methodExpression);
    final TextRange rhsRange = getSingleParameterRange(expression);

    final IElementType operator;
    final TextRange parentRange;
    if (isNegated(expression.getParent())) {
      operator = JavaTokenType.NE;
      parentRange = expression.getParent().getTextRange();
      results.add(new NamedFoldingDescriptor(expression,
                                             parentRange.getStartOffset(), lhsRange.getStartOffset(),
                                             group, EMPTY_PLACEHOLDER));
    }
    else {
      operator = JavaTokenType.EQEQ;
      parentRange = expression.getTextRange();
    }

    Collections.addAll(results,
                       new NamedFoldingDescriptor(expression,
                                                  lhsRange.getEndOffset(), rhsRange.getStartOffset(), group,
                                                  getPlaceholderOperator(operator)),
                       new NamedFoldingDescriptor(expression,
                                                  rhsRange.getEndOffset(), parentRange.getEndOffset(), group,
                                                  EMPTY_PLACEHOLDER));
    return results;
  }

  @SuppressWarnings("ConstantConditions")
  private static @NotNull TextRange getCallerRange(@NotNull final PsiReferenceExpression methodExpression) {
    return methodExpression.getQualifierExpression().getTextRange();
  }

  @SuppressWarnings("ConstantConditions")
  private static @NotNull TextRange getSingleParameterRange(@NotNull final PsiCall expression) {
    final PsiExpression[] parameterList = expression.getArgumentList().getExpressions();
    if (parameterList.length != 1) throw new IllegalArgumentException("One parameter was expected!");

    return parameterList[0].getTextRange();
  }

  private static boolean isNegated(@NotNull final PsiElement parent) {
    return parent instanceof PsiPrefixExpression
           && JavaTokenType.EXCL.equals(((PsiPrefixExpression)parent).getOperationTokenType());
  }

  private static @NotNull PsiBinaryExpression getBinaryParent(@NotNull final PsiElement expression) {
    return (PsiBinaryExpression)expression.getParent();
  }

  @SuppressWarnings("ConstantConditions")
  private static @NotNull Collection<NamedFoldingDescriptor> getSignumFolds(@NotNull final PsiMethodCallExpression methodCallExpression,
                                                                            @NotNull final PsiReferenceExpression expression,
                                                                            @NotNull final PsiBinaryExpression parent) {
    if (methodCallExpression.getTypeArguments().length != 0) throw new IllegalStateException("The method " + SIGNUM_METHOD_NAME +" shouldn't have parameters!");

    final int rangeStart = getCallerRange(expression).getEndOffset();
    final int rangeEnd = parent.getROperand().getTextRange().getStartOffset();

    final FoldingGroup group = FoldingGroup.newGroup(COMPARISON_GROUP_NAME);

    final NamedFoldingDescriptor operator = new NamedFoldingDescriptor(parent,
                                                                       rangeStart, rangeEnd, group,
                                                                       getPlaceholderOperator(parent.getOperationSign().getTokenType()));
    return Collections.singletonList(operator);
  }

  private static @NotNull Collection<NamedFoldingDescriptor> getCompareToFolds(@NotNull final PsiCall expression,
                                                                               @NotNull final PsiReferenceExpression methodExpression,
                                                                               @NotNull final PsiBinaryExpression parent) {
    final FoldingGroup group = FoldingGroup.newGroup(COMPARISON_GROUP_NAME);

    final TextRange lhsRange = getCallerRange(methodExpression);
    final TextRange rhsRange = getSingleParameterRange(expression);

    final NamedFoldingDescriptor operator = new NamedFoldingDescriptor(parent,
                                                                       lhsRange.getEndOffset(), rhsRange.getStartOffset(), group,
                                                                       getPlaceholderOperator(getComparableTokenType(parent)));
    final NamedFoldingDescriptor end = new NamedFoldingDescriptor(parent,
                                                                  rhsRange.getEndOffset(), parent.getTextRange().getEndOffset(),
                                                                  group, EMPTY_PLACEHOLDER);
    return Arrays.asList(operator, end);
  }

  @SuppressWarnings("ConstantConditions")
  private static @NotNull IElementType getComparableTokenType(@NotNull final PsiBinaryExpression parent) {
    IElementType tokenType = parent.getOperationSign().getTokenType();
    Object rhs = computeConstantExpression(parent.getROperand(), false);
    if (rhs != null) {
      switch (parseInt(rhs.toString())) {
        case -1:
          if ((tokenType == JavaTokenType.GT) || (tokenType == JavaTokenType.NE)) { /* > -1 or != -1 are interpreted as ≥ 0 */
            return JavaTokenType.GE;
          }
        case 0:
          return tokenType;
        case 1:
          if ((tokenType == JavaTokenType.LT) || (tokenType == JavaTokenType.NE)) { /* < +1 or != +1 are interpreted as ≤ 0*/
            return JavaTokenType.LE;
          }
      }
    }
    throw new IllegalStateException("Unknown rhs!");
  }

  private static @NotNull String getPlaceholderOperator(@NotNull final IElementType type) {
    return " " + OPERATOR_REPLACEMENTS.get(type) + " ";
  }
}