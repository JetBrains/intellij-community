// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.lang.jvm.JvmMethod
import com.intellij.model.SymbolReference
import com.intellij.model.search.SearchSymbolReferenceParameters
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UnfairTextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMemberReference
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.util.Processor

class ImplicitConstructorCallSearcher : QueryExecutorBase<SymbolReference, SearchSymbolReferenceParameters>(true) {

  override fun processQuery(queryParameters: SearchSymbolReferenceParameters, consumer: Processor<in SymbolReference>) {
    if (!Registry.`is`("ide.symbol.reference.search")) return
    val target = queryParameters.target as? PsiMethod ?: return
    if (!target.isConstructor) return
    if (!canBeCalledImplicitly(target)) {
      return
    }
    val clazz = target.containingClass ?: return
    val manager = PsiManager.getInstance(queryParameters.project)
    processImplicitConstructorCall(clazz, target, manager, clazz, consumer)
  }
}

private fun canBeCalledImplicitly(target: JvmMethod): Boolean {
  val parameters = target.parameters
  return parameters.isEmpty() || parameters.size == 1 && target.isVarArgs
}

private fun processImplicitConstructorCall(usage: PsiMember,
                                           target: PsiMethod,
                                           manager: PsiManager,
                                           containingClass: PsiClass,
                                           processor: Processor<in PsiReference>): Boolean {
  if (containingClass is PsiAnonymousClass) return true

  val ctrClass = target.containingClass ?: return true

  val isImplicitSuper = manager.areElementsEquivalent(ctrClass, containingClass.superClass)
  if (!isImplicitSuper) {
    return true
  }

  val resolved = JavaResolveUtil.resolveImaginarySuperCallInThisPlace(usage, manager.project, ctrClass)

  val resolvesToThisConstructor = manager.areElementsEquivalent(target, resolved)
  if (!resolvesToThisConstructor) {
    return true
  }

  return processor.process(object : LightMemberReference(manager, usage, PsiSubstitutor.EMPTY) {

    override fun getElement(): PsiElement = usage

    override fun getRangeInElement(): TextRange {
      if (usage is PsiNameIdentifierOwner) {
        val identifier = (usage as PsiNameIdentifierOwner).nameIdentifier
        if (identifier != null) {
          val startOffsetInParent = identifier.startOffsetInParent
          return if (startOffsetInParent >= 0) { // -1 for light elements generated e.g. by lombok
            TextRange.from(startOffsetInParent, identifier.textLength)
          }
          else {
            UnfairTextRange(-1, -1)
          }
        }
      }
      return super.getRangeInElement()
    }
  })
}