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

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl
import com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl
import com.intellij.psi.util.TypeConversionUtil


object JavaInlayHintsProvider {

  fun hints(callExpression: PsiCallExpression): Set<InlayInfo> {
    val resolveResult = callExpression.resolveMethodGenerics()
    val hints = methodHints(callExpression, resolveResult)
    if (hints.isNotEmpty()) return hints
    
    return when (callExpression) {
      is PsiMethodCallExpressionImpl -> mergedHints(callExpression, callExpression.methodExpression.multiResolve(false))
      is PsiNewExpressionImpl -> mergedHints(callExpression, callExpression.constructorFakeReference.multiResolve(false))
      else -> emptySet()
    }
  }

  private fun mergedHints(callExpression: PsiCallExpression,
                          results: Array<out ResolveResult>): Set<InlayInfo> {
    val resultSet = results
      .filter { it.element != null }
      .map { methodHints(callExpression, it) }

    if (resultSet.isEmpty()) return emptySet()
    if (resultSet.size == 1) {
      return resultSet.first()
    }

    //we can show hints for same named parameters of overloaded methods, even if don't know exact method
    return resultSet.reduce { left, right -> left.intersect(right) }
      .map { InlayInfo(it.text, it.offset, isShowOnlyIfExistedBefore = true) }
      .toSet()
  }

  private fun methodHints(callExpression: PsiCallExpression, resolveResult: ResolveResult): Set<InlayInfo> {
    val element = resolveResult.element
    val substitutor = (resolveResult as? JavaResolveResult)?.substitutor ?: PsiSubstitutor.EMPTY
    
    if (element is PsiMethod && isMethodToShow(element, callExpression)) {
      val info = callInfo(callExpression, element)
      return hintSet(info, substitutor)
    }
    
    return emptySet()
  }

  private fun hintSet(info: CallInfo, substitutor: PsiSubstitutor): Set<InlayInfo> {
    val resultSet = mutableSetOf<InlayInfo>()

    val varargInlay = info.varargsInlay(substitutor)
    if (varargInlay != null) {
      resultSet.add(varargInlay)
    }
    
    if (isShowForParamsWithSameType()) {
      resultSet.addAll(info.sameTypeInlays())
    }
    
    resultSet.addAll(info.unclearInlays(substitutor))
    
    return resultSet
  }

  private fun isShowForParamsWithSameType() = JavaInlayParameterHintsProvider.getInstance().isShowForParamsWithSameType.get()

  private fun isMethodToShow(method: PsiMethod, callExpression: PsiCallExpression): Boolean {
    val params = method.parameterList.parameters
    if (params.isEmpty()) return false
    if (params.size == 1) {
      val hintsProvider = JavaInlayParameterHintsProvider.getInstance()
      
      if (hintsProvider.isDoNotShowForBuilderLikeMethods.get() 
          && isBuilderLike(callExpression, method)) {
        return false
      }
      
      if (hintsProvider.isDoNotShowIfMethodNameContainsParameterName.get()
          && isParamNameContainedInMethodName(params[0], method)) {
        return false
      }
    }
    return true
  }
  
  
  private fun isBuilderLike(expression: PsiCallExpression, method: PsiMethod): Boolean {
    if (expression is PsiNewExpression) return false

    val returnType = TypeConversionUtil.erasure(method.returnType) ?: return false
    val calledMethodClassFqn = method.containingClass?.qualifiedName ?: return false

    return returnType.equalsToText(calledMethodClassFqn)
  }
  
  private fun isParamNameContainedInMethodName(parameter: PsiParameter, method: PsiMethod): Boolean {
    val parameterName = parameter.name ?: return false
    if (parameterName.length > 1) {
      return method.name.contains(parameterName, ignoreCase = true)
    }
    return false
  }
  
  private fun callInfo(callExpression: PsiCallExpression, method: PsiMethod): CallInfo {
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
  
}


private fun inlayInfo(info: CallArgumentInfo, showOnlyIfExistedBefore: Boolean = false): InlayInfo? {
  return inlayInfo(info.argument, info.parameter, showOnlyIfExistedBefore)
}


private fun inlayInfo(callArgument: PsiExpression, methodParam: PsiParameter, showOnlyIfExistedBefore: Boolean = false): InlayInfo? {
  val paramName = methodParam.name ?: return null
  val paramToShow = (if (methodParam.type is PsiEllipsisType) "..." else "") + paramName
  return InlayInfo(paramToShow, callArgument.textRange.startOffset, showOnlyIfExistedBefore)
}


private fun isUnclearExpression(callArgument: PsiElement): Boolean {
  val isShowHint = when (callArgument) {
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

  return isShowHint
}


private class CallInfo(val regularArgs: List<CallArgumentInfo>, val varArg: PsiParameter?, val varArgExpressions: List<PsiExpression>) {
  
  
  fun unclearInlays(substitutor: PsiSubstitutor): List<InlayInfo> {
    val inlays = mutableListOf<InlayInfo>() 
    
    for (callInfo in regularArgs) {
      val inlay = when {
        isUnclearExpression(callInfo.argument) -> inlayInfo(callInfo)
        !callInfo.isAssignable(substitutor) -> inlayInfo(callInfo, showOnlyIfExistedBefore = true)
        else -> null
      }
      
      inlay?.let { inlays.add(inlay) }
    }
    
    return inlays
  }

  
  fun sameTypeInlays(): List<InlayInfo> {
    val all = regularArgs.map { it.parameter.typeText() }
    val duplicated = all.toMutableList()

    all.distinct().forEach {
      duplicated.remove(it)
    }

    return regularArgs
      .filter { duplicated.contains(it.parameter.typeText()) }
      .mapNotNull { inlayInfo(it) }
  }

  
  fun varargsInlay(substitutor: PsiSubstitutor): InlayInfo? {
    if (varArg == null) return null

    var hasUnassignable = false
    for (expr in varArgExpressions) {
      if (isUnclearExpression(expr)) {
        return inlayInfo(varArgExpressions.first(), varArg)
      }
      hasUnassignable = hasUnassignable || !varArg.isAssignable(expr, substitutor)
    }
    
    return if (hasUnassignable) inlayInfo(varArgExpressions.first(), varArg, showOnlyIfExistedBefore = true) else null
  }
  
}


private class CallArgumentInfo(val parameter: PsiParameter, val argument: PsiExpression) {
  fun isAssignable(substitutor: PsiSubstitutor): Boolean {
    return parameter.isAssignable(argument, substitutor)
  }
}


private fun PsiParameter.isAssignable(argument: PsiExpression, substitutor: PsiSubstitutor = PsiSubstitutor.EMPTY): Boolean {
  val substitutedType = substitutor.substitute(type)
  if (PsiPolyExpressionUtil.isPolyExpression(argument)) return true
  return argument.type?.isAssignableTo(substitutedType) ?: false
}


private fun PsiType.isAssignableTo(parameterType: PsiType): Boolean {
  return TypeConversionUtil.isAssignable(parameterType, this)
}


private fun PsiParameter.typeText() = type.canonicalText