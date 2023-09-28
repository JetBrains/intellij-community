// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.fix

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.shortenReference

class CallReplacementInfo(val qualifiedReference: String?,
                          val methodReplacementInfos: List<MethodReplacementInfo>) {
  override fun toString(): String = if (qualifiedReference == null) {
    methodReplacementInfos.joinToString(separator = "().", postfix = "()") { it.name }
  }
  else if (methodReplacementInfos.isEmpty()) {
    qualifiedReference
  }
  else {
    "$qualifiedReference.${methodReplacementInfos.joinToString(separator = "().", postfix = "()") { it.name }}"
  }
}

class MethodReplacementInfo(val name: String,
                            val returnType: PsiType? = null,
                            val parameters: List<SmartPsiElementPointer<PsiElement>> = emptyList())

fun UCallExpression.replaceWithCallChain(callReplacementInfo: CallReplacementInfo) {
  val element = sourcePsi ?: return
  val project = element.project
  val uFactory = getUastElementFactory(project)
  val oldUParent = getQualifiedParentOrThis()
  val uQualifiedReference = callReplacementInfo.qualifiedReference?.let { name ->
    uFactory?.createQualifiedReference(name, null)
  } ?: receiver

  val newUElement = callReplacementInfo.methodReplacementInfos.fold(uQualifiedReference) { receiver, method ->
    val uExpressions = method.parameters.mapNotNull { it.element.toUElementOfType<UExpression>() }

    if (uExpressions.size != method.parameters.size) {
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