// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.fix

import com.intellij.codeInspection.toSmartPsiElementPointer
import com.intellij.psi.PsiType
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.shortenReference

/**
 * Class represents info about the reference to the class and chain of methods, which will replace the old link
 * @param qualifiedReference defines the FQN for the reference. If null, methods called without receiver
 * @param callReplacementInfos defines information about methods,
 */
class CallChainReplacementInfo(val qualifiedReference: String?,
                               vararg val callReplacementInfos: CallReplacementInfo) {
  fun presentation(): String = if (qualifiedReference == null) {
    callReplacementInfos.joinToString(separator = "().", postfix = "()") { it.name }
  }
  else if (callReplacementInfos.isEmpty()) {
    qualifiedReference
  }
  else {
    "$qualifiedReference.${callReplacementInfos.joinToString(separator = "().", postfix = "()") { it.name }}"
  }
}

/**
 * Class represents info about the method, which will be a part of replacement reference procedure
 * @see CallChainReplacementInfo
 * @param name defines FQN name of the method
 * @param returnType defines method's return type. If null, then we consider, that method returns Void
 */
class CallReplacementInfo(val name: String,
                          val returnType: PsiType? = null,
                          vararg parameters: UExpression) {
  val parametersPointers = parameters.map { it.toSmartPsiElementPointer() }
}

/**
 * @see CallChainReplacementInfo and
 * @see CallReplacementInfo
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
    val uExpressions = method.parametersPointers.mapNotNull { it?.element.toUElementOfType<UExpression>() }

    if (uExpressions.size != method.parametersPointers.size) {
      return@replaceWithCallChain
    }

    uFactory?.createCallExpression(receiver?.getQualifiedParentOrThis(),
                                   method.name,
                                   uExpressions,
                                   method.returnType,
                                   UastCallKind.METHOD_CALL)
  }

  val oldPsi = oldUParent.sourcePsi ?: return
  val newPsi = newUElement?.getQualifiedParentOrThis()?.sourcePsi ?: return
  oldPsi.replace(newPsi).toUElementOfType<UReferenceExpression>()?.shortenReference()
}