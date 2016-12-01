/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.hints;

import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class JavaParameterHintManager {

  @NotNull
  private final Set<InlayInfo> myDescriptors;

  public JavaParameterHintManager(@NotNull PsiCallExpression callExpression) {
    PsiExpression[] callArguments = getArguments(callExpression);
    JavaResolveResult resolveResult = callExpression.resolveMethodGenerics();
    if (!(resolveResult.getElement() instanceof PsiMethod)) {
      myDescriptors = Collections.emptySet();
      return;
    }

    final PsiMethod method = (PsiMethod)resolveResult.getElement();

    Set<InlayInfo> descriptors = Collections.emptySet();
    if (isMethodToShowParams(callExpression, method)) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      descriptors = buildDescriptorsForLiteralArguments(callArguments, parameters, resolveResult);
    }

    myDescriptors = descriptors;
  }

  private static boolean isMethodToShowParams(@NotNull PsiCallExpression callExpression, @NotNull PsiMethod method) {
    if (isSetter(method)) return false;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 1) {
      if (isBuilder(callExpression, method)) return false;

      ParameterNameHintsSettings settings = ParameterNameHintsSettings.getInstance();
      if (!settings.isShowParamNameContainedInMethodName()
          && isParamNameContainedInMethodName(parameters[0], method)) {
        return false;
      }
    }

    return true;
  }

  private static Set<String> getDuplicatedParamTypes(PsiParameter[] parameters) {
    List<String> duplicatedTypes = Arrays
      .stream(parameters)
      .map((p) -> p.getType().getCanonicalText())
      .collect(Collectors.toList());

    ContainerUtil.newHashSet(duplicatedTypes).forEach((e) -> duplicatedTypes.remove(e));
    return ContainerUtil.newHashSet(duplicatedTypes);
  }

  private static boolean isParamNameContainedInMethodName(@NotNull PsiParameter parameter, @NotNull PsiMethod method) {
    String parameterName = parameter.getName();
    if (parameterName != null && parameterName.length() > 1) {
      String methodName = method.getName();
      return StringUtil.containsIgnoreCase(methodName, parameterName);
    }
    return false;
  }

  private static boolean isBuilder(PsiCallExpression expression, PsiMethod method) {
    if (expression instanceof PsiNewExpression || !hasSingleParameter(method)) {
      return false;
    }

    PsiType returnType = TypeConversionUtil.erasure(method.getReturnType());
    final PsiClass aClass = method.getContainingClass();
    final String calledMethodFqn = aClass != null ? aClass.getQualifiedName() : null;
    if (calledMethodFqn != null && returnType != null) {
      return returnType.equalsToText(calledMethodFqn);
    }

    return false;
  }

  private static boolean hasSingleParameter(PsiMethod method) {
    return method.getParameterList().getParametersCount() == 1;
  }


  private static boolean isSetter(PsiMethod method) {
    String methodName = method.getName();
    if (hasSingleParameter(method) && methodName.startsWith("set")
        && (methodName.length() == 3
            || methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3)))) {
      return true;
    }
    return false;
  }

  static boolean isUnclearExpression(@Nullable PsiElement callArgument) {
    if (callArgument instanceof PsiLiteralExpression) {
      return true;
    }

    if (callArgument instanceof PsiPrefixExpression) {
      PsiPrefixExpression expr = (PsiPrefixExpression)callArgument;
      IElementType tokenType = expr.getOperationTokenType();
      return (JavaTokenType.MINUS.equals(tokenType)
              || JavaTokenType.PLUS.equals(tokenType)) && expr.getOperand() instanceof PsiLiteralExpression;
    }

    if (callArgument instanceof PsiThisExpression
        || callArgument instanceof PsiBinaryExpression
        || callArgument instanceof PsiPolyadicExpression) {
      return true;
    }

    return false;
  }

  @NotNull
  private static PsiExpression[] getArguments(@NotNull PsiCallExpression call) {
    PsiExpressionList callArgumentsList = call.getArgumentList();
    return callArgumentsList == null ? PsiExpression.EMPTY_ARRAY : callArgumentsList.getExpressions();
  }

  @NotNull
  public Set<InlayInfo> getDescriptors() {
    return myDescriptors;
  }

  @NotNull
  private static Set<InlayInfo> buildDescriptorsForLiteralArguments(@NotNull PsiExpression[] callArguments,
                                                                    @NotNull PsiParameter[] parameters,
                                                                    @NotNull JavaResolveResult resolveResult) {

    final Set<InlayInfo> descriptors = ContainerUtil.newHashSet();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();

    if (ParameterNameHintsSettings.getInstance().isShowForParamsWithSameType()) {
      final List<InlayInfo> hintsForNonLiterals = getHintsForParamsWithSameType(callArguments, parameters, substitutor);
      descriptors.addAll(hintsForNonLiterals);
    }

    for (int i = 0; i < Math.min(callArguments.length, parameters.length); i++) {
      PsiExpression arg = callArguments[i];
      PsiParameter param = parameters[i];

      if (isVarargParam(param.getType(), arg.getType()) && hasUnclearExpressionStartingFrom(i, callArguments)
          || isUnclearExpression(arg) && isAssignable(arg, param, substitutor)) {
        descriptors.add(createInlayInfo(arg, param));
      }
    }

    return descriptors;
  }

  private static List<InlayInfo> getHintsForParamsWithSameType(@NotNull PsiExpression[] callArguments,
                                                               @NotNull PsiParameter[] parameters,
                                                               @NotNull PsiSubstitutor substitutor)
  {
    final Set<String> duplicatedTypes = getDuplicatedParamTypes(parameters);

    final int minArraySize = Math.min(callArguments.length, parameters.length);
    List<PsiExpression> args = ContainerUtil.list(callArguments).subList(0, minArraySize);
    List<PsiParameter> params = ContainerUtil.list(parameters).subList(0, minArraySize);

    return StreamEx
      .zip(args, params, (a, b) -> Pair.create(a, b))
      .filter((pair) -> {
        PsiParameter param = pair.getSecond();
        return duplicatedTypes.contains(param.getType().getCanonicalText());
      })
      .filter((pair) -> isAssignable(pair.first, pair.second, substitutor))
      .map((pair) -> createInlayInfo(pair.first, pair.second))
      .collect(Collectors.toList());
  }

  @NotNull
  private static InlayInfo createInlayInfo(@NotNull PsiExpression callArgument, @NotNull PsiParameter methodParam) {
    String paramName = ((methodParam.getType() instanceof PsiEllipsisType) ? "..." : "") + methodParam.getName();
    return new InlayInfo(paramName, callArgument.getTextRange().getStartOffset());
  }

  private static boolean isAssignable(@NotNull PsiExpression argument,
                                      @NotNull PsiParameter parameter,
                                      @NotNull PsiSubstitutor substitutor) {
    PsiType argType = argument.getType();
    PsiType paramType = parameter.getType();

    if (argType != null) {
      PsiType parameterType = substitutor.substitute(paramType);
      return TypeConversionUtil.isAssignable(parameterType, argType);
    }

    return false;
  }

  private static boolean hasUnclearExpressionStartingFrom(int index, PsiExpression[] callArguments) {
    for (int i = index; i < callArguments.length; i++) {
      PsiExpression arg = callArguments[i];
      if (isUnclearExpression(arg)) return true;
    }
    return false;
  }

  private static boolean isVarargParam(@Nullable PsiType paramType, @Nullable PsiType argType) {
    if (paramType == null || argType == null) return false;
    PsiType deepType = paramType.getDeepComponentType();
    return paramType instanceof PsiEllipsisType && TypeConversionUtil.isAssignable(deepType, argType);
  }

  private static boolean hasUnclearExpressions(@NotNull PsiExpression[] arguments) {
    for (PsiExpression argument : arguments) {
      if (isUnclearExpression(argument)) return true;
    }
    return false;
  }
}
