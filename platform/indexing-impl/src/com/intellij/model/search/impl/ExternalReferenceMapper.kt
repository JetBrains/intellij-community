// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.walkUp

class ExternalReferenceMapper(
  private val targetPointer: Pointer<out Symbol>
) : LeafOccurrenceMapper<PsiSymbolReference> {

  override fun mapOccurrence(scope: PsiElement, start: PsiElement, offsetInStart: Int): Collection<PsiSymbolReference> {
    val target: Symbol = targetPointer.dereference() ?: return emptyList()
    for ((element, offsetInElement) in walkUp(start, offsetInStart, scope)) {
      if (element !is PsiExternalReferenceHost) {
        continue
      }
      val hints = object : PsiSymbolReferenceHints {
        override fun getTarget(): Symbol = target
        override fun getOffsetInElement(): Int = offsetInElement
      }
      val externalReferences: Iterable<PsiSymbolReference> = PsiSymbolReferenceService.getService().getExternalReferences(element, hints)
      return externalReferences.filter { reference ->
        ProgressManager.checkCanceled()
        reference.resolvesTo(target)
      }
    }
    return emptyList()
  }
}
