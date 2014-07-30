/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ParameterNameFoldingManager {
  private static final int MIN_NAME_LENGTH_THRESHOLD = 3;
  private static final int MIN_ARGS_TO_FOLD = 2;

  private static final List<Couple<String>> COMMONLY_USED_PARAMETER_PAIR = ContainerUtil.newArrayList(
    Couple.of("begin", "end"),
    Couple.of("start", "end"),
    Couple.of("first", "last"),
    Couple.of("first", "second"),
    Couple.of("from", "to"),
    Couple.of("key", "value"),
    Couple.of("min", "max")
  );

  private final PsiCallExpression myCallExpression;

  private PsiExpression[] myCallArguments;
  private PsiParameter[] myParameters;

  public ParameterNameFoldingManager(@NotNull PsiCallExpression callExpression) {
    myCallExpression = callExpression;
  }

  public static boolean isLiteralExpression(@Nullable PsiElement callArgument) {
    if (callArgument instanceof PsiLiteralExpression)
      return true;

    if (callArgument instanceof PsiPrefixExpression) {
      PsiPrefixExpression expr = (PsiPrefixExpression)callArgument;
      IElementType tokenType = expr.getOperationTokenType();
      return (JavaTokenType.MINUS.equals(tokenType)
              || JavaTokenType.PLUS.equals(tokenType)) && expr.getOperand() instanceof PsiLiteralExpression;
    }

    return false;
  }

  @Nullable
  public PsiExpression[] getArguments(@NotNull PsiCallExpression call) {
    PsiExpressionList callArgumentsList = call.getArgumentList();
    return callArgumentsList != null ? callArgumentsList.getExpressions() : null;
  }

  @NotNull
  public List<FoldingDescriptor> buildDescriptors() {
    myCallArguments = getArguments(myCallExpression);

    if (myCallArguments != null && myCallArguments.length >= MIN_ARGS_TO_FOLD && hasLiteralExpression(myCallArguments)) {
      PsiMethod method = myCallExpression.resolveMethod();

      if (method != null) {
        myParameters = method.getParameterList().getParameters();
        if (myParameters.length == myCallArguments.length) {
          return buildDescriptorsForLiteralArguments();
        }
      }
    }

    return ContainerUtil.emptyList();
  }

  @NotNull
  private List<FoldingDescriptor> buildDescriptorsForLiteralArguments() {
    List<FoldingDescriptor> descriptors = ContainerUtil.newArrayList();

    int i = 0;
    while (i < myCallArguments.length) {
      if (i + 1 < myCallArguments.length && isCommonlyNamedParameterPair(i, i + 1)) {
        i += 2;
        continue;
      }

      if (shouldInlineParameterName(i)) {
        descriptors.add(createFoldingDescriptor(myCallArguments[i], myParameters[i]));
      }
      i++;
    }

    return descriptors;
  }

  @NotNull
  private static NamedFoldingDescriptor createFoldingDescriptor(@NotNull PsiExpression callArgument, @NotNull PsiParameter methodParam) {
    TextRange range = callArgument.getTextRange();
    String placeholderText = methodParam.getName() + ": " + callArgument.getText();
    return new NamedFoldingDescriptor(callArgument, range.getStartOffset(), range.getEndOffset(), null, placeholderText);
  }

  private boolean isCommonlyNamedParameterPair(int first, int second) {
    assert first < myParameters.length && second < myParameters.length;

    String firstParamName = myParameters[first].getName();
    String secondParamName = myParameters[second].getName();
    if (firstParamName == null || secondParamName == null) return false;

    for (Couple<String> knownPair : COMMONLY_USED_PARAMETER_PAIR) {
      if (StringUtil.containsIgnoreCase(firstParamName, knownPair.first)
          && StringUtil.containsIgnoreCase(secondParamName, knownPair.second)) {
        return true;
      }
    }

    return false;
  }

  private boolean shouldInlineParameterName(int paramIndex) {
    PsiExpression argument = myCallArguments[paramIndex];
    if (isLiteralExpression(argument) && argument.getType() != null) {
      PsiParameter parameter = myParameters[paramIndex];
      String paramName = parameter.getName();
      if (paramName != null && paramName.length() >= MIN_NAME_LENGTH_THRESHOLD) {
        return TypeConversionUtil.isAssignable(parameter.getType(), argument.getType());
      }
    }
    return false;
  }

  private static boolean hasLiteralExpression(@NotNull PsiExpression[] arguments) {
    for (PsiExpression argument : arguments) {
      if (isLiteralExpression(argument)) return true;
    }
    return false;
  }
}
