// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference

import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInspection.dataFlow.*
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.*
import com.intellij.codeInspection.dataFlow.inference.ContractInferenceInterpreter.withConstraint
import com.intellij.codeInspection.dataFlow.java.inst.MethodCallInstruction
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.psiutils.MethodUtils
import com.siyeh.ig.psiutils.SideEffectChecker

public interface PreContract {
  public fun toContracts(method: PsiMethod, body: () -> PsiCodeBlock): List<StandardMethodContract>
  public fun negate(): PreContract? = NegatingContract(
    this)
}

internal data class KnownContract(val contract: StandardMethodContract) : PreContract {
  override fun toContracts(method: PsiMethod, body: () -> PsiCodeBlock): List<StandardMethodContract> = listOf(contract)
  override fun negate(): KnownContract? = negateContract(contract)?.let(::KnownContract)
}

internal data class DelegationContract(internal val expression: ExpressionRange, internal val negated: Boolean) : PreContract {

  override fun toContracts(method: PsiMethod, body: () -> PsiCodeBlock): List<StandardMethodContract> {
    val call : PsiMethodCallExpression = expression.restoreExpression(body())

    val result = call.resolveMethodGenerics()
    val targetMethod = result.element as PsiMethod? ?: return emptyList()
    if (targetMethod == method) return emptyList()

    val parameters = targetMethod.parameterList.parameters
    val arguments = call.argumentList.expressions
    val qualifier = PsiUtil.skipParenthesizedExprDown(call.methodExpression.qualifierExpression)
    val varArgCall = MethodCallInstruction.isVarArgCall(targetMethod, result.substitutor, arguments, parameters)

    var methodContracts: List<StandardMethodContract> = StandardMethodContract.toNonIntersectingStandardContracts(
      JavaMethodContractUtil.getMethodContracts(targetMethod))
                                                        ?: return emptyList()
    if (MethodUtils.isEquals(targetMethod) && qualifier is PsiReferenceExpression) {
      val target = qualifier.resolve() as? PsiField
      if (target?.containingClass?.qualifiedName == CommonClassNames.JAVA_LANG_BOOLEAN) {
        val constraint = when (target.name) {
          "FALSE" -> FALSE_VALUE
          "TRUE" -> TRUE_VALUE
          else -> null
        }
        if (constraint != null) {
          methodContracts = listOf(StandardMethodContract(arrayOf(constraint), ContractReturnValue.returnTrue()),
                                   StandardMethodContract(arrayOf(constraint.negate()), ContractReturnValue.returnFalse())) +
                            (if (arguments.firstOrNull()?.type is PsiPrimitiveType) listOf() else 
                              listOf(StandardMethodContract(arrayOf(NULL_VALUE), ContractReturnValue.returnFalse()))) 
        }
      }
    }

    val fromDelegate = methodContracts.mapNotNull { dc ->
      convertDelegatedMethodContract(method, parameters, qualifier, arguments, varArgCall, dc)
    }.toMutableList()
    while (fromDelegate.isNotEmpty() && fromDelegate[fromDelegate.size - 1].returnValue == ContractReturnValue.returnAny()) {
      fromDelegate.removeAt(fromDelegate.size - 1)
    }
    if (NullableNotNullManager.isNotNull(targetMethod)) {
      fromDelegate += listOf(
        StandardMethodContract(emptyConstraints(method), ContractReturnValue.returnNotNull()))
    }
    return fromDelegate
  }

  private fun convertDelegatedMethodContract(callerMethod: PsiMethod,
                                             targetParameters: Array<PsiParameter>,
                                             qualifier: PsiExpression?,
                                             callArguments: Array<PsiExpression>,
                                             varArgCall: Boolean,
                                             targetContract: StandardMethodContract): StandardMethodContract? {
    var answer: Array<StandardMethodContract.ValueConstraint>? = emptyConstraints(callerMethod)
    for (i in 0 until targetContract.parameterCount) {
      if (i >= callArguments.size) return null
      val argConstraint = targetContract.getParameterConstraint(i)
      if (argConstraint != ANY_VALUE) {
        if (varArgCall && i >= targetParameters.size - 1) {
          if (argConstraint == NULL_VALUE) {
            return null
          }
          break
        }

        val argument = PsiUtil.skipParenthesizedExprDown(callArguments[i]) ?: return null
        val paramIndex = resolveParameter(callerMethod, argument)
        if (paramIndex >= 0) {
          answer = withConstraint(answer, paramIndex, argConstraint) ?: return null
        }
        else if (argConstraint != getLiteralConstraint(argument)) {
          return null
        }
      }
    }
    var returnValue = targetContract.returnValue
    returnValue = when (returnValue) {
      is ContractReturnValue.BooleanReturnValue -> {
        if (negated) returnValue.negate() else returnValue
      }
      is ContractReturnValue.ParameterReturnValue -> {
        mapReturnValue(callerMethod, callArguments[returnValue.parameterNumber])
      }
      ContractReturnValue.returnThis() -> {
        mapReturnValue(callerMethod, qualifier)
      }
      else -> returnValue
    }
    return answer?.let { StandardMethodContract(it, returnValue) }
  }

  private fun mapReturnValue(callerMethod: PsiMethod, argument: PsiExpression?): ContractReturnValue? {
    val stripped = PsiUtil.skipParenthesizedExprDown(argument)
    val paramIndex = resolveParameter(callerMethod, stripped)
    return when {
      paramIndex >= 0 -> ContractReturnValue.returnParameter(paramIndex)
      stripped is PsiLiteralExpression -> when (stripped.value) {
        null -> ContractReturnValue.returnNull()
        true -> ContractReturnValue.returnTrue()
        false -> ContractReturnValue.returnFalse()
        else -> ContractReturnValue.returnNotNull()
      }
      stripped is PsiThisExpression && stripped.qualifier == null -> ContractReturnValue.returnThis()
      stripped is PsiNewExpression -> ContractReturnValue.returnNew()
      NullabilityUtil.getExpressionNullability(stripped) == Nullability.NOT_NULL -> ContractReturnValue.returnNotNull()
      else -> ContractReturnValue.returnAny()
    }
  }

  private fun emptyConstraints(method: PsiMethod) = StandardMethodContract.createConstraintArray(
    method.parameterList.parametersCount)

  private fun returnNotNull(mc: StandardMethodContract): StandardMethodContract {
    return if (mc.returnValue.isFail) mc else mc.withReturnValue(ContractReturnValue.returnNotNull())
  }

  private fun getLiteralConstraint(argument: PsiExpression) = when (argument) {
    is PsiLiteralExpression -> ContractInferenceInterpreter.getLiteralConstraint(
      argument.getFirstChild().node.elementType)
    is PsiNewExpression, is PsiPolyadicExpression, is PsiFunctionalExpression -> NOT_NULL_VALUE
    else -> null
  }

  private fun resolveParameter(method: PsiMethod, expr: PsiExpression?): Int {
    val target = if (expr is PsiReferenceExpression && !expr.isQualified) expr.resolve() else null
    return if (target is PsiParameter && target.parent === method.parameterList) method.parameterList.getParameterIndex(target) else -1
  }
}

internal data class SideEffectFilter(internal val expressionsToCheck: List<ExpressionRange>, internal val contracts: List<PreContract>) : PreContract {

  override fun toContracts(method: PsiMethod, body: () -> PsiCodeBlock): List<StandardMethodContract> {
    if (expressionsToCheck.any { d -> mayHaveSideEffects(body(), d) }) {
      return emptyList()
    }
    return contracts.flatMap { c -> c.toContracts(method, body) }
  }

  private fun mayHaveSideEffects(body: PsiCodeBlock, range: ExpressionRange) =
    range.restoreExpression<PsiExpression>(body).let { SideEffectChecker.mayHaveSideEffects(it) }
}

internal data class NegatingContract(internal val negated: PreContract) : PreContract {
  override fun toContracts(method: PsiMethod, body: () -> PsiCodeBlock): List<StandardMethodContract> = negated.toContracts(method, body).mapNotNull(::negateContract)
}

private fun negateContract(c: StandardMethodContract): StandardMethodContract? {
  val ret = c.returnValue
  return if (ret is ContractReturnValue.BooleanReturnValue) c.withReturnValue(ret.negate())
  else null
}

@Suppress("EqualsOrHashCode")
internal data class MethodCallContract(internal val call: ExpressionRange, internal val states: List<List<StandardMethodContract.ValueConstraint>>) : PreContract {
  override fun hashCode(): Int = call.hashCode() * 31 + states.flatten().map { it.ordinal }.hashCode()

  override fun toContracts(method: PsiMethod, body: () -> PsiCodeBlock): List<StandardMethodContract> {
    val target = call.restoreExpression<PsiMethodCallExpression>(body()).resolveMethod()
    if (target != null && target != method && NullableNotNullManager.isNotNull(target)) {
      return ContractInferenceInterpreter.toContracts(states.map { it.toTypedArray() }, ContractReturnValue.returnNotNull())
    }
    return emptyList()
  }
}
