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

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParameterNameFoldingManager {
  private static final List<Couple<String>> COMMONLY_USED_PARAMETER_PAIR = ContainerUtil.newArrayList(
    Couple.of("begin", "end"),
    Couple.of("start", "end"),
    Couple.of("first", "last"),
    Couple.of("first", "second"),
    Couple.of("from", "to"),
    Couple.of("key", "value"),
    Couple.of("min", "max")
  );

  @NotNull
  private final List<FoldingDescriptor> myDescriptors;

  public ParameterNameFoldingManager(@NotNull PsiCallExpression callExpression) {
    PsiExpression[] callArguments = getArguments(callExpression);
    JavaResolveResult resolveResult = callExpression.resolveMethodGenerics();

    JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
    List<FoldingDescriptor> descriptors = Collections.emptyList();
    if (callArguments.length >= settings.getInlineLiteralParameterMinArgumentsToFold() &&
        hasLiteralExpression(callArguments) &&
        resolveResult.getElement() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)resolveResult.getElement();
      PsiParameter[] parameters = method.getParameterList().getParameters();
      descriptors = buildDescriptorsForLiteralArguments(callArguments, parameters, resolveResult);
    }

    myDescriptors = descriptors;
  }

  static boolean isLiteralExpression(@Nullable PsiElement callArgument) {
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

  @NotNull
  private static PsiExpression[] getArguments(@NotNull PsiCallExpression call) {
    PsiExpressionList callArgumentsList = call.getArgumentList();
    return callArgumentsList == null ? PsiExpression.EMPTY_ARRAY : callArgumentsList.getExpressions();
  }

  @NotNull
  public List<FoldingDescriptor> getDescriptors() {
    return myDescriptors;
  }

  @NotNull
  private static List<FoldingDescriptor> buildDescriptorsForLiteralArguments(@NotNull PsiExpression[] callArguments,
                                                                             @NotNull PsiParameter[] parameters,
                                                                             @NotNull JavaResolveResult resolveResult) {
    List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();

    int i = 0;
    while (i < callArguments.length && i < parameters.length) {
      if (i + 1 < callArguments.length && isCommonlyNamedParameterPair(i, i + 1, parameters)) {
        i += 2;
        continue;
      }

      if (!(parameters[i].getType() instanceof PsiEllipsisType) && shouldInlineParameterName(i, callArguments, parameters, resolveResult)) {
        descriptors.add(createFoldingDescriptor(callArguments[i], parameters[i]));
      }
      i++;
    }

    return descriptors;
  }

  @NotNull
  private static NamedFoldingDescriptor createFoldingDescriptor(@NotNull PsiExpression callArgument, @NotNull PsiParameter methodParam) {
    PsiElement lParenOrCommaOrWhitespaceOrComment = callArgument.getPrevSibling();
    TextRange range = lParenOrCommaOrWhitespaceOrComment.getTextRange();
    String placeholderText = StringUtil.last(lParenOrCommaOrWhitespaceOrComment.getText(), 1, false) + methodParam.getName() + ": ";
    return new NamedFoldingDescriptor(callArgument, range.getEndOffset()-1, range.getEndOffset(), null, placeholderText);
  }

  private static boolean isCommonlyNamedParameterPair(int first, int second, PsiParameter[] parameters) {
    if (!(first < parameters.length && second < parameters.length)) return false;

    String firstParamName = parameters[first].getName();
    String secondParamName = parameters[second].getName();
    if (firstParamName == null || secondParamName == null) return false;

    for (Couple<String> knownPair : COMMONLY_USED_PARAMETER_PAIR) {
      if (StringUtil.containsIgnoreCase(firstParamName, knownPair.first)
          && StringUtil.containsIgnoreCase(secondParamName, knownPair.second)) {
        return true;
      }
    }

    return false;
  }

  private static boolean shouldInlineParameterName(int paramIndex,
                                                   @NotNull PsiExpression[] callArguments,
                                                   @NotNull PsiParameter[] parameters,
                                                   @NotNull JavaResolveResult resolveResult) {
    PsiExpression argument = callArguments[paramIndex];
    if (isLiteralExpression(argument) && argument.getType() != null) {
      PsiParameter parameter = parameters[paramIndex];
      String paramName = parameter.getName();
      JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
      if (paramName != null && paramName.length() >= settings.getInlineLiteralParameterMinNameLength()) {
        PsiType parameterType = resolveResult.getSubstitutor().substitute(parameter.getType());
        return TypeConversionUtil.isAssignable(parameterType, argument.getType());
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
