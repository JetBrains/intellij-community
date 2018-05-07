// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInspection.dataFlow.ContractReturnValue
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil
import com.intellij.codeInspection.dataFlow.StandardMethodContract
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.ANY_VALUE
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.NULL_VALUE
import com.intellij.codeInspection.dataFlow.inference.ContractInferenceInterpreter.withConstraint
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction
import com.intellij.psi.*
import com.siyeh.ig.psiutils.SideEffectChecker

/**
 * @author peter
 */
interface PreContract {
  fun toContracts(method: PsiMethod, body: () -> PsiCodeBlock): List<StandardMethodContract>
  fun negate(): PreContract? = NegatingContract(
    this)
}

internal data class KnownContract(val contract: StandardMethodContract) : PreContract {
  override fun toContracts(method: PsiMethod, body: () -> PsiCodeBlock) = listOf(contract)
  override fun negate() = negateContract(contract)?.let(::KnownContract)
}

internal data class DelegationContract(internal val expression: ExpressionRange, internal val negated: Boolean) : PreContract {

  override fun toContracts(method: PsiMethod, body: () -> PsiCodeBlock): List<StandardMethodContract> {
    val call = expression.restoreExpression(body()) as PsiMethodCallExpression? ?: return emptyList()

    val result = call.resolveMethodGenerics()
    val targetMethod = result.element as PsiMethod? ?: return emptyList()

    val parameters = targetMethod.parameterList.parameters
    val arguments = call.argumentList.expressions
    val varArgCall = MethodCallInstruction.isVarArgCall(targetMethod, result.substitutor, arguments, parameters)

    val fromDelegate = JavaMethodContractUtil.getMethodContracts(targetMethod).mapNotNull { dc ->
      convertDelegatedMethodContract(method, parameters, arguments, varArgCall, dc)
    }
    if (NullableNotNullManager.isNotNull(targetMethod)) {
      return fromDelegate.map { returnNotNull(it) } + listOf(
        StandardMethodContract(emptyConstraints(method), ContractReturnValue.returnNotNull()))
    }
    return fromDelegate
  }

  private fun convertDelegatedMethodContract(callerMethod: PsiMethod,
                                             targetParameters: Array<PsiParameter>,
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

        val argument = callArguments[i]
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
    if (negated && returnValue is ContractReturnValue.BooleanReturnValue) returnValue = returnValue.negate()
    return answer?.let { StandardMethodContract(it, returnValue) }
  }

  private fun emptyConstraints(method: PsiMethod) = StandardMethodContract.createConstraintArray(
    method.parameterList.parametersCount)

  private fun returnNotNull(mc: StandardMethodContract): StandardMethodContract {
    return if (mc.returnValue.isFail) mc else mc.withReturnValue(ContractReturnValue.returnNotNull())
  }

  private fun getLiteralConstraint(argument: PsiExpression) = when (argument) {
    is PsiLiteralExpression -> ContractInferenceInterpreter.getLiteralConstraint(
      argument.getFirstChild().node.elementType)
    else -> null
  }

  private fun resolveParameter(method: PsiMethod, expr: PsiExpression): Int {
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
      range.restoreExpression(body)?.let { SideEffectChecker.mayHaveSideEffects(it) } ?: false
}

internal data class NegatingContract(internal val negated: PreContract) : PreContract {
  override fun toContracts(method: PsiMethod, body: () -> PsiCodeBlock) = negated.toContracts(method, body).mapNotNull(::negateContract)
}

private fun negateContract(c: StandardMethodContract): StandardMethodContract? {
  val ret = c.returnValue
  return if (ret is ContractReturnValue.BooleanReturnValue) c.withReturnValue(ret.negate())
  else null
}

@Suppress("EqualsOrHashCode")
internal data class MethodCallContract(internal val call: ExpressionRange, internal val states: List<List<StandardMethodContract.ValueConstraint>>) : PreContract {
  override fun hashCode() = call.hashCode() * 31 + states.flatten().map { it.ordinal }.hashCode()

  override fun toContracts(method: PsiMethod, body: () -> PsiCodeBlock): List<StandardMethodContract> {
    val target = (call.restoreExpression(body()) as PsiMethodCallExpression?)?.resolveMethod()
    if (target != null && NullableNotNullManager.isNotNull(target)) {
      return ContractInferenceInterpreter.toContracts(states.map { it.toTypedArray() }, ContractReturnValue.returnNotNull())
    }
    return emptyList()
  }
}
