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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.util.Couple;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.containsIgnoreCase;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class ParameterNameHintsManager {
  private static final List<Couple<String>> COMMONLY_USED_PARAMETER_PAIR = ContainerUtil.newArrayList(
    Couple.of("begin", "end"),
    Couple.of("start", "end"),
    Couple.of("first", "last"),
    Couple.of("first", "second"),
    Couple.of("from", "to"),
    Couple.of("key", "value"),
    Couple.of("min", "max"),
    Couple.of("format", "arg")
  );

  private static final Set<Character> ALLOWED_PARAMETER_NAME_CHARS = ContainerUtil.newHashSet('x', 'y', 'z', 'w', 'h');
  
  private static final Set<String> COMMON_METHOD_NAMES = ContainerUtil.newHashSet(
    "get", "set", "contains", "append", "print", "println", "charAt", "startsWith", "endsWith", "indexOf"
  );
  
  @NotNull
  private final List<InlayInfo> myDescriptors;

  public ParameterNameHintsManager(@NotNull PsiCallExpression callExpression) {
    PsiExpression[] callArguments = getArguments(callExpression);
    JavaResolveResult resolveResult = callExpression.resolveMethodGenerics();
    
    List<InlayInfo> descriptors = Collections.emptyList();
    if (resolveResult.getElement() instanceof PsiMethod
        && isMethodToShowParams(resolveResult)
        && hasUnclearExpressions(callArguments)) 
    {
      PsiMethod method = (PsiMethod)resolveResult.getElement();
      PsiParameter[] parameters = method.getParameterList().getParameters();
      descriptors = buildDescriptorsForLiteralArguments(callArguments, parameters, resolveResult);
    }

    myDescriptors = descriptors;
  }

  private static boolean isMethodToShowParams(JavaResolveResult resolveResult) {
    PsiElement element = resolveResult.getElement();
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      return !isSetter(method) && !isCommonMethod(method);
    }
    return false;
  }

  private static boolean isCommonMethod(PsiMethod method) {
    return COMMON_METHOD_NAMES.contains(method.getName());
  }

  private static boolean isSetter(PsiMethod method) {
    String methodName = method.getName();
    if (method.getParameterList().getParametersCount() == 1
        && methodName.startsWith("set")
        && methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3))) {
      return true;
    }
    return false;
  }

  static boolean isUnclearExpression(@Nullable PsiElement callArgument) {
    if (callArgument instanceof PsiLiteralExpression)
      return true;

    if (callArgument instanceof PsiPrefixExpression) {
      PsiPrefixExpression expr = (PsiPrefixExpression)callArgument;
      IElementType tokenType = expr.getOperationTokenType();
      return (JavaTokenType.MINUS.equals(tokenType)
              || JavaTokenType.PLUS.equals(tokenType)) && expr.getOperand() instanceof PsiLiteralExpression;
    }

    if (callArgument instanceof PsiThisExpression) {
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
  public List<InlayInfo> getDescriptors() {
    return myDescriptors;
  }

  @NotNull
  private static List<InlayInfo> buildDescriptorsForLiteralArguments(@NotNull PsiExpression[] callArguments,
                                                                     @NotNull PsiParameter[] parameters,
                                                                     @NotNull JavaResolveResult resolveResult) {
    
    List<InlayInfo> descriptors = ContainerUtil.newArrayList();
    for (int i = 0; i < Math.min(callArguments.length, parameters.length); i++) {
      PsiExpression arg = callArguments[i];
      PsiParameter param = parameters[i];

      if (isVarargParam(param.getType(), arg.getType()) && hasUnclearExpressionStartingFrom(i, callArguments) 
          || shouldInlineParameterName(arg, param, resolveResult)) {
        descriptors.add(createInlayInfo(arg, param));
      }
    }

    final int totalDescriptors = descriptors.size();
    if (totalDescriptors == 1 && shouldIgnoreSingleHint(parameters, descriptors)
        || totalDescriptors == 2 && parameters.length == 2 && isParamPairToIgnore(descriptors.get(0), descriptors.get(1))
        || countOneCharLengthHints(descriptors) == totalDescriptors && !containsAnyMeaningfull(descriptors)) {
      return ContainerUtil.emptyList();
    }
    
    return descriptors;
  }


  private static long countOneCharLengthHints(List<InlayInfo> inlays) {
    return inlays.stream().filter((e) -> e.getText().length() == 1).count();
  }

  private static boolean shouldIgnoreSingleHint(@NotNull PsiParameter[] parameters, List<InlayInfo> descriptors) {
    return isStringLiteral(descriptors.get(0)) && !hasMultipleStringParams(parameters);
  }

  private static boolean containsAnyMeaningfull(List<InlayInfo> descriptors) {
    return descriptors.stream().anyMatch((e) -> {
      String text = e.getText();
      return text.length() == 1 && ALLOWED_PARAMETER_NAME_CHARS.contains(text.charAt(0));
    });
  }

  private static boolean hasMultipleStringParams(PsiParameter[] parameters) {
    int stringParams = 0;
    for (PsiParameter parameter : parameters) {
      if (parameter.getType().equalsToText(JAVA_LANG_STRING)) {
        stringParams++;
      }
    }
    return stringParams > 1;
  }

  private static boolean isStringLiteral(InlayInfo info) {
    PsiType type = info.getArgument().getType();
    return type != null && type.equalsToText(JAVA_LANG_STRING);
  }

  @NotNull
  private static InlayInfo createInlayInfo(@NotNull PsiExpression callArgument, @NotNull PsiParameter methodParam) {
    String paramName = ((methodParam.getType() instanceof PsiEllipsisType) ? "..." : "") + methodParam.getName();
    return new InlayInfo(paramName, callArgument.getTextRange().getStartOffset(), callArgument);
  }

  private static boolean isParamPairToIgnore(InlayInfo first, InlayInfo second) {
    String firstParamName = first.getText();
    String secondParamName = second.getText();

    for (Couple<String> knownPair : COMMONLY_USED_PARAMETER_PAIR) {
      if (containsIgnoreCase(firstParamName, knownPair.first) 
          && containsIgnoreCase(secondParamName, knownPair.second)) {
        return true;
      }
    }

    return false;
  }

  private static boolean shouldInlineParameterName(@NotNull PsiExpression argument,
                                                   @NotNull PsiParameter parameter,
                                                   @NotNull JavaResolveResult resolveResult) {
    PsiType argType = argument.getType();
    PsiType paramType = parameter.getType();
    
    if (argType != null && isUnclearExpression(argument)) {
      PsiType parameterType = resolveResult.getSubstitutor().substitute(paramType);
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
