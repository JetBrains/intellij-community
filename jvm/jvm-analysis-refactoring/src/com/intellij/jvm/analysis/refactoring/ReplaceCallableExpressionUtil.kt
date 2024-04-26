// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jvm.analysis.refactoring

import com.intellij.codeInsight.intention.FileModifier.SafeTypeForPreview
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.shortenReference

/**
 * A call chains that can be used for a replacement by [replaceWithCallChain]
 *
 * Example:
 * ```
 * org.jetbrains.Foo.bar().fooBar()
 * ```
 * Here `org.jetbrains.Foo` is the [qualifiedReference] and `bar()` and `fooBar()` are the [callReplacementInfos].
 *
 * @param qualifiedReference the receiver of the call chain, null for methods without a receiver
 * @param callReplacementInfos the call chain
 */
@SafeTypeForPreview
class CallChainReplacementInfo(val qualifiedReference: String?, vararg val callReplacementInfos: CallReplacementInfo) {
  private val qualifierPresentation get() = if (qualifiedReference != null) {
    "$qualifiedReference" + if (callReplacementInfos.isNotEmpty()) "." else ""
  } else ""

  private val callPresentation get() = if (callReplacementInfos.isNotEmpty()) {
    callReplacementInfos.joinToString(separator = "().", postfix = "()", transform = CallReplacementInfo::name)
  } else ""

  val presentation: String get() = qualifierPresentation + callPresentation
}

/**
 * Call info that can be used for a replacement used by [CallChainReplacementInfo].
 */
@SafeTypeForPreview
class CallReplacementInfo(val name: String, val returnType: PsiType? = null, vararg parameters: UExpression) {
  val parametersPointers: List<SmartPsiElementPointer<PsiElement>> = parameters.mapNotNull(UExpression::toSmartPsiElementPointer)
}

/**
 * Replaces a [UCallExpression] with a call as described by [CallChainReplacementInfo].
 */
@RequiresWriteLock
fun UCallExpression.replaceWithCallChain(callChainReplacementInfo: CallChainReplacementInfo) {
  val element = sourcePsi ?: return
  val project = element.project
  val uFactory = getUastElementFactory(project)
  val oldUParent = getQualifiedParentOrThis()
  val uQualifiedReference = callChainReplacementInfo.qualifiedReference?.let { name ->
    uFactory?.createQualifiedReference(name, null)
  } ?: receiver

  val newUElement = callChainReplacementInfo.callReplacementInfos.fold(uQualifiedReference) { receiver, method ->
    val uExpressions = method.parametersPointers.mapNotNull { it.element.toUElementOfType<UExpression>() }
    if (uExpressions.size != method.parametersPointers.size) return@replaceWithCallChain
    uFactory?.createCallExpression(
      receiver?.getQualifiedParentOrThis(),
      method.name,
      uExpressions,
      method.returnType,
      UastCallKind.METHOD_CALL
    )
  }

  val oldPsi = oldUParent.sourcePsi ?: return
  val newPsi = newUElement?.getQualifiedParentOrThis()?.sourcePsi ?: return
  oldPsi.replace(newPsi).toUElementOfType<UReferenceExpression>()?.shortenReference()
}