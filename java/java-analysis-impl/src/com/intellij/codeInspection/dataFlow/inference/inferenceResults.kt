// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference

import com.intellij.codeInsight.ExpressionUtil
import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInspection.dataFlow.Mutability
import com.intellij.codeInspection.dataFlow.MutationSignature
import com.intellij.lang.LighterASTNode
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.psiutils.ClassUtils
import java.util.*

/**
 * @author peter
 */
data class ExpressionRange internal constructor (val startOffset: Int, val endOffset: Int) {

  companion object {
    @JvmStatic
    fun create(expr: LighterASTNode, scopeStart: Int): ExpressionRange = ExpressionRange(
      expr.startOffset - scopeStart, expr.endOffset - scopeStart)
  }

  inline fun <reified T : PsiExpression> restoreExpression(scope: PsiCodeBlock): T {
    val scopeStart = scope.textRange.startOffset
    val element = PsiTreeUtil.findElementOfClassAtRange(scope.containingFile, startOffset + scopeStart,
                                                                          endOffset + scopeStart,
                                                        T::class.java)
    if (element == null) {
      throw CannotRestoreExpressionException("No expression of type " + T::class + " found")
    }
    return element
  }

}

data class PurityInferenceResult(internal val mutatesThis: Boolean,
                                 internal val mutatedRefs: List<ExpressionRange>, 
                                 internal val singleCall: ExpressionRange?) {

  fun getMutationSignature(method: PsiMethod, body: () -> PsiCodeBlock): MutationSignature =
    when {
      mutatesNonLocals(method, body) -> MutationSignature.unknown()
      mutatesThis -> fromCalls(method, body).alsoMutatesThis()
      else -> fromCalls(method, body)
    }

  private fun mutatesNonLocals(method: PsiMethod, body: () -> PsiCodeBlock): Boolean {
    return mutatedRefs.any { range -> !isLocalVarReference(range.restoreExpression(body()), method) }
  }

  private fun fromCalls(currentMethod: PsiMethod, body: () -> PsiCodeBlock): MutationSignature {
    if (singleCall == null) return MutationSignature.pure()

    val psiCall : PsiCallExpression = singleCall.restoreExpression(body())
    val method = psiCall.resolveMethod()
    if (method == currentMethod) {
      if (!mutatesThis || psiCall is PsiMethodCallExpression && ExpressionUtil.isEffectivelyUnqualified(psiCall.methodExpression)) {
        return MutationSignature.pure()
      }
      return MutationSignature.unknown()
    }
    val signature = MutationSignature.fromCall(psiCall)
    if (signature == MutationSignature.pure() ||
        signature == MutationSignature.pure().alsoMutatesThis() &&
        psiCall is PsiMethodCallExpression && ExpressionUtil.isEffectivelyUnqualified(psiCall.methodExpression)) {
      return if (currentMethod.isConstructor) MutationSignature.pure() else signature
    }
    return MutationSignature.unknown()
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
  fun getNullability(method: PsiMethod, body: () -> PsiCodeBlock): Nullability
  fun getMutability(method: PsiMethod, body: () -> PsiCodeBlock): Mutability = Mutability.UNKNOWN

  @Suppress("EqualsOrHashCode")
  data class Predefined(internal val value: Nullability) : MethodReturnInferenceResult {
    override fun hashCode(): Int = value.ordinal
    override fun getNullability(method: PsiMethod, body: () -> PsiCodeBlock): Nullability = value
  }

  data class FromDelegate(internal val value: Nullability, internal val delegateCalls: List<ExpressionRange>) : MethodReturnInferenceResult {
    override fun getNullability(method: PsiMethod, body: () -> PsiCodeBlock): Nullability {
      return when {
        value == Nullability.NULLABLE -> Nullability.NULLABLE 
        delegateCalls.all { range -> isNotNullCall(method, range, body()) } -> Nullability.NOT_NULL
        else -> Nullability.UNKNOWN
      }
    }

    override fun getMutability(method: PsiMethod, body: () -> PsiCodeBlock): Mutability {
      if (value == Nullability.NOT_NULL) {
        return Mutability.UNKNOWN
      }
      return delegateCalls.stream().map { range -> getDelegateMutability(method, range, body()) }.reduce(
        Mutability::join).orElse(
        Mutability.UNKNOWN)
    }

    private fun getDelegateMutability(caller: PsiMethod, delegate: ExpressionRange, body: PsiCodeBlock): Mutability {
      val call : PsiMethodCallExpression = delegate.restoreExpression(body) 
      val target = call.resolveMethod()
      return when {
        target == null || target == caller -> Mutability.UNKNOWN
        ClassUtils.isImmutable(target.returnType, false) -> Mutability.UNMODIFIABLE
        else -> Mutability.getMutability(target)
      }
    }

    private fun isNotNullCall(caller: PsiMethod, delegate: ExpressionRange, body: PsiCodeBlock): Boolean {
      val call : PsiMethodCallExpression = delegate.restoreExpression(body) 
      if (call.type is PsiPrimitiveType) return true

      val target = call.resolveMethod()
      return target == caller || target != null && NullableNotNullManager.isNotNull(target)
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

  @Volatile
  private var myDetachedBody: PsiCodeBlock? = null

  fun methodBody(method: PsiMethodImpl): () -> PsiCodeBlock = {
    if (method.stub != null) {
      var detached = myDetachedBody
      if (detached == null) {
        detached = getDetachedBody(method)
        myDetachedBody = detached
      } else {
        assert(detached.parent == method || detached.containingFile.context == method)
      }
      detached
    }
    else
      method.body!!
  }

  private fun getDetachedBody(method: PsiMethodImpl): PsiCodeBlock {
    val document = method.containingFile.viewProvider.document ?: return method.body!!
    try {
      val bodyText = PsiDocumentManager.getInstance(method.project).getLastCommittedText(document).substring(bodyStart, bodyEnd)
      return JavaPsiFacade.getElementFactory(method.project).createCodeBlockFromText(bodyText, method)
    }
    catch (e: RuntimeException) {
      throw handleInconsistency(method, this, e)
    }
  }
}