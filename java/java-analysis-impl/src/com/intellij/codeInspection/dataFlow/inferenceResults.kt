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
package com.intellij.codeInspection.dataFlow

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.lang.LighterASTNode
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import java.util.*

/**
 * @author peter
 */
data class ExpressionRange internal constructor (internal val startOffset: Int, internal val endOffset: Int) {

  companion object {
    @JvmStatic
    fun create(expr: LighterASTNode, scopeStart: Int) = ExpressionRange(expr.startOffset - scopeStart, expr.endOffset - scopeStart)
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
    return called != null && ControlFlowAnalyzer.isPure(called)
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


interface NullityInferenceResult {
  fun getNullness(method: PsiMethod, body: () -> PsiCodeBlock): Nullness

  @Suppress("EqualsOrHashCode")
  data class Predefined(internal val value: Nullness) : NullityInferenceResult {
    override fun hashCode() = value.ordinal
    override fun getNullness(method: PsiMethod, body: () -> PsiCodeBlock) = when {
      value == Nullness.NULLABLE && InferenceFromSourceUtil.suppressNullable(method) -> Nullness.UNKNOWN
      else -> value
    }
  }

  data class FromDelegate(internal val delegateCalls: List<ExpressionRange>) : NullityInferenceResult {
    override fun getNullness(method: PsiMethod, body: () -> PsiCodeBlock) = when {
      delegateCalls.all { range -> isNotNullCall(range, body()) } -> Nullness.NOT_NULL
      else -> Nullness.UNKNOWN
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
    val nullity: NullityInferenceResult?,
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