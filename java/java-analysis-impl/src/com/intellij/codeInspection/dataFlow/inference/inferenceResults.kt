// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil
import com.intellij.codeInspection.dataFlow.Mutability
import com.intellij.codeInspection.dataFlow.Nullness
import com.intellij.lang.LighterASTNode
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.psiutils.ClassUtils
import java.util.*

/**
 * @author peter
 */
data class ExpressionRange internal constructor (internal val startOffset: Int, internal val endOffset: Int) {

  companion object {
    @JvmStatic
    fun create(expr: LighterASTNode, scopeStart: Int) = ExpressionRange(
      expr.startOffset - scopeStart, expr.endOffset - scopeStart)
  }

  fun restoreExpression(scope: PsiCodeBlock): PsiExpression? {
    val scopeStart = scope.textRange.startOffset
    return PsiTreeUtil.findElementOfClassAtRange(scope.containingFile, startOffset + scopeStart, endOffset + scopeStart,
                                                 PsiExpression::class.java)
  }

}

data class PurityInferenceResult(internal val mutatedRefs: List<ExpressionRange>, internal val singleCall: ExpressionRange?) {

  fun isPure(method: PsiMethod, body: () -> PsiCodeBlock) = !mutatesNonLocals(method, body) && callsOnlyPureMethods(body)

  private fun mutatesNonLocals(method: PsiMethod, body: () -> PsiCodeBlock): Boolean {
    return mutatedRefs.any { range -> !isLocalVarReference(range.restoreExpression(body()), method) }
  }

  private fun callsOnlyPureMethods(body: () -> PsiCodeBlock): Boolean {
    if (singleCall == null) return true

    val called = (singleCall.restoreExpression(body()) as PsiCall).resolveMethod()
    return called != null && JavaMethodContractUtil.isPure(called)
  }

  private fun isLocalVarReference(expression: PsiExpression?, scope: PsiMethod): Boolean {
    return when (expression) {
      is PsiReferenceExpression -> expression.resolve().let { it is PsiLocalVariable || it is PsiParameter }
      is PsiArrayAccessExpression -> (expression.arrayExpression as? PsiReferenceExpression)?.resolve().let { target ->
        target is PsiLocalVariable && isLocallyCreatedArray(scope, target)
      }
      else -> false
    }
  }

  private fun isLocallyCreatedArray(scope: PsiMethod, target: PsiLocalVariable): Boolean {
    val initializer = target.initializer
    if (initializer != null && initializer !is PsiNewExpression) {
      return false
    }

    for (ref in ReferencesSearch.search(target, LocalSearchScope(scope)).findAll()) {
      if (ref is PsiReferenceExpression && PsiUtil.isAccessedForWriting(ref)) {
        val assign = PsiTreeUtil.getParentOfType(ref, PsiAssignmentExpression::class.java)
        if (assign == null || assign.rExpression !is PsiNewExpression) {
          return false
        }
      }
    }
    return true
  }
}


interface MethodReturnInferenceResult {
  fun getNullness(method: PsiMethod, body: () -> PsiCodeBlock): Nullness
  fun getMutability(method: PsiMethod, body: () -> PsiCodeBlock): Mutability = Mutability.UNKNOWN

  @Suppress("EqualsOrHashCode")
  data class Predefined(internal val value: Nullness) : MethodReturnInferenceResult {
    override fun hashCode() = value.ordinal
    override fun getNullness(method: PsiMethod, body: () -> PsiCodeBlock) = when {
      value == Nullness.NULLABLE && InferenceFromSourceUtil.suppressNullable(
        method) -> Nullness.UNKNOWN
      else -> value
    }
  }

  data class FromDelegate(internal val value: Nullness, internal val delegateCalls: List<ExpressionRange>) : MethodReturnInferenceResult {
    override fun getNullness(method: PsiMethod, body: () -> PsiCodeBlock): Nullness {
      if (value == Nullness.NULLABLE) {
        return if (InferenceFromSourceUtil.suppressNullable(
            method)) Nullness.UNKNOWN
        else Nullness.NULLABLE
      }
      return when {
        delegateCalls.all { range -> isNotNullCall(range, body()) } -> Nullness.NOT_NULL
        else -> Nullness.UNKNOWN
      }
    }

    override fun getMutability(method: PsiMethod, body: () -> PsiCodeBlock): Mutability {
      if (value == Nullness.NOT_NULL) {
        return Mutability.UNKNOWN
      }
      return delegateCalls.stream().map { range -> getDelegateMutability(range, body()) }.reduce(
        Mutability::union).orElse(
        Mutability.UNKNOWN)
    }

    private fun getDelegateMutability(delegate: ExpressionRange, body: PsiCodeBlock): Mutability {
      val call = delegate.restoreExpression(body) as PsiMethodCallExpression
      val target = call.resolveMethod()
      return when {
        target == null -> Mutability.UNKNOWN
        ClassUtils.isImmutable(target.returnType, false) -> Mutability.UNMODIFIABLE
        else -> Mutability.getMutability(target)
      }
    }

    private fun isNotNullCall(delegate: ExpressionRange, body: PsiCodeBlock): Boolean {
      val call = delegate.restoreExpression(body) as PsiMethodCallExpression
      if (call.type is PsiPrimitiveType) return true

      val target = call.resolveMethod()
      return target != null && NullableNotNullManager.isNotNull(target)
    }
  }
}

data class MethodData(
  val methodReturn: MethodReturnInferenceResult?,
  val purity: PurityInferenceResult?,
  val contracts: List<PreContract>,
  val notNullParameters: BitSet,
  internal val bodyStart: Int,
  internal val bodyEnd: Int
) {
  fun methodBody(method: PsiMethodImpl): () -> PsiCodeBlock = {
    if (method.stub != null)
      CachedValuesManager.getCachedValue(method) { CachedValueProvider.Result(getDetachedBody(method), method) }
    else
      method.body!!
  }

  private fun getDetachedBody(method: PsiMethod): PsiCodeBlock {
    val document = method.containingFile.viewProvider.document ?: return method.body!!
    val bodyText = PsiDocumentManager.getInstance(method.project).getLastCommittedText(document).substring(bodyStart, bodyEnd)
    return JavaPsiFacade.getElementFactory(method.project).createCodeBlockFromText(bodyText, method)
  }
}