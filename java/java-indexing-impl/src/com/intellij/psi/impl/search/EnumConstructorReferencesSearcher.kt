// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.SymbolReference
import com.intellij.model.search.SymbolReferenceSearchParameters
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiMethod
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor

class EnumConstructorReferencesSearcher : QueryExecutor<SymbolReference, SymbolReferenceSearchParameters> {

  override fun execute(queryParameters: SymbolReferenceSearchParameters, consumer: Processor<in SymbolReference>): Boolean {
    if (!Registry.`is`("ide.symbol.reference.search")) return true
    val method = queryParameters.target as? PsiMethod ?: return true
    return DumbService.getInstance(queryParameters.project).runReadActionInSmartMode<Boolean> {
      executeSmart(method, consumer)
    }
  }

  private fun executeSmart(method: PsiMethod, consumer: Processor<in SymbolReference>): Boolean {
    if (!method.isConstructor) return true
    val clazz = method.containingClass ?: return true
    for (field in clazz.fields) {
      if (field !is PsiEnumConstant) continue
      val reference = field.getReference() ?: continue
      if (!reference.isReferenceTo(method)) continue
      if (!consumer.process(reference)) {
        return false
      }
    }
    return true
  }
}

