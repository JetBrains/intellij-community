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
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil


object JavaInlayHintsProvider {

  fun createHints(callExpression: PsiCallExpression): Set<InlayInfo> {
    val (element, substitutor) = callExpression.resolveMethodGenerics().let { it.element to it.substitutor }
    if (element is PsiMethod && isMethodToShow(element, callExpression)) {
      val info = getCallInfo(callExpression, element)
      return createHintSet(info, substitutor)
    }
    return emptySet()
  }

  private fun createHintSet(info: CallInfo, substitutor: PsiSubstitutor): Set<InlayInfo> {
    val args = info.regularArgs.filter { it.isAssignable(substitutor) }

    val resultSet = mutableSetOf<InlayInfo>()
    with(resultSet) {
      getVarArgInlay(info)?.let { add(it) }

      if (ParameterNameHintsSettings.getInstance().isShowForParamsWithSameType) {
        addAll(createSameTypeInlays(args))
      }

      addAll(createUnclearInlays(args))
    }

    return resultSet
  }

  private fun isMethodToShow(method: PsiMethod, callExpression: PsiCallExpression): Boolean {
    val params = method.parameterList.parameters
    if (params.isEmpty()) return false
    if (params.size == 1) {
      if (isBuilderLike(callExpression, method) || isSetterNamed(method)) return false
      if (!ParameterNameHintsSettings.getInstance().isShowParamNameContainedInMethodName
          && isParamNameContainedInMethodName(params[0], method)) return false
    }
    return true
  }

  private fun createUnclearInlays(args: List<CallArgumentInfo>): List<InlayInfo> {
    return args
      .filter { isUnclearExpression(it.argument) }
      .mapNotNull { createInlayInfo(it.argument, it.parameter) }
  }

  private fun getVarArgInlay(info: CallInfo): InlayInfo? {
    if (info.varArg == null || info.varArgExpressions.isEmpty()) return null
    val hasUnclearExpressions = info.varArgExpressions.find { isUnclearExpression(it) } != null
    if (hasUnclearExpressions) {
      return createInlayInfo(info.varArgExpressions.first(), info.varArg)
    }
    return null
  }

  private fun isBuilderLike(expression: PsiCallExpression, method: PsiMethod): Boolean {
    if (expression is PsiNewExpression) return false

    val returnType = TypeConversionUtil.erasure(method.returnType) ?: return false
    val calledMethodClassFqn = method.containingClass?.qualifiedName ?: return false

    return returnType.equalsToText(calledMethodClassFqn)
  }

  private fun isSetterNamed(method: PsiMethod): Boolean {
    val methodName = method.name
    if (methodName.startsWith("set")
        && (methodName.length == 3 || methodName.length > 3 && methodName[3].isUpperCase())) {
      return true
    }
    return false
  }

  private fun isParamNameContainedInMethodName(parameter: PsiParameter, method: PsiMethod): Boolean {
    val parameterName = parameter.name ?: return false
    if (parameterName.length > 1) {
      return method.name.contains(parameterName, ignoreCase = true)
    }
    return false
  }

  private fun createSameTypeInlays(args: List<CallArgumentInfo>): List<InlayInfo> {
    val all = args.map { it.parameter.typeText() }
    val duplicated = all.toMutableList()

    all.distinct().forEach {
      duplicated.remove(it)
    }

    return args
      .filter { duplicated.contains(it.parameter.typeText()) }
      .mapNotNull { createInlayInfo(it.argument, it.parameter) }
  }

  private fun createInlayInfo(callArgument: PsiExpression, methodParam: PsiParameter): InlayInfo? {
    val paramName = methodParam.name ?: return null
    val paramToShow = (if (methodParam.type is PsiEllipsisType) "..." else "") + paramName
    return InlayInfo(paramToShow, callArgument.textRange.startOffset)
  }

  private fun getCallInfo(callExpression: PsiCallExpression, method: PsiMethod): CallInfo {
    val params = method.parameterList.parameters
    val hasVarArg = params.lastOrNull()?.isVarArgs ?: false
    val regularParamsCount = if (hasVarArg) params.size - 1 else params.size

    val arguments = callExpression.argumentList?.expressions ?: emptyArray()

    val regularArgInfos = params
      .take(regularParamsCount)
      .zip(arguments)
      .map { CallArgumentInfo(it.first, it.second) }

    val varargParam = if (hasVarArg) params.last() else null
    val varargExpressions = arguments.drop(regularParamsCount)
    return CallInfo(regularArgInfos, varargParam, varargExpressions)
  }

  private fun isUnclearExpression(callArgument: PsiElement): Boolean {
    return when (callArgument) {
      is PsiLiteralExpression -> true
      is PsiThisExpression -> true
      is PsiBinaryExpression -> true
      is PsiPolyadicExpression -> true
      is PsiPrefixExpression -> {
        val tokenType = callArgument.operationTokenType
        val isLiteral = callArgument.operand is PsiLiteralExpression
        isLiteral && (JavaTokenType.MINUS == tokenType || JavaTokenType.PLUS == tokenType)
      }
      else -> false
    }
  }
}


private class CallInfo(val regularArgs: List<CallArgumentInfo>,
                       val varArg: PsiParameter?,
                       val varArgExpressions: List<PsiExpression>)


private class CallArgumentInfo(val parameter: PsiParameter, val argument: PsiExpression)


private fun CallArgumentInfo.isAssignable(substitutor: PsiSubstitutor): Boolean {
  val substitutedType = substitutor.substitute(parameter.type)
  return argument.type?.isAssignableTo(substitutedType) ?: false
}


private fun PsiType.isAssignableTo(parameterType: PsiType): Boolean {
  return TypeConversionUtil.isAssignable(parameterType, this)
}


private fun PsiParameter.typeText() = type.canonicalText