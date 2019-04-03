// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.SymbolReference
import com.intellij.model.SymbolService
import com.intellij.model.search.SearchRequestor
import com.intellij.model.search.SearchSymbolReferenceParameters
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Query

/**
 * Includes [ReferencesSearch] results into [com.intellij.model.search.SymbolReferenceSearch] results.
 *
 * @see SymbolToPsiReferenceSearcher
 */
class PsiToSymbolSearchRequestor : SearchRequestor {

  override fun collectSearchRequests(parameters: SearchSymbolReferenceParameters): Collection<Query<out SymbolReference>> {
    if (parameters is SymbolToPsiReferenceSearcher.PsiToSymbolParameters) {
      // search started from ReferencesSearch
      // -> SymbolToPsiReferenceSearcher queries SymbolReferenceSearch with PsiToSymbolParameters
      // -> don't query ReferenceSearch again because we started from there
      return emptyList()
    }
    val psiElement = SymbolService.getPsiElement(parameters.target) ?: return emptyList()
    val psiSearchParameters = SymbolToPsiParameters(psiElement, parameters)
    return listOf(ReferencesSearch.search(psiSearchParameters))
  }

  internal class SymbolToPsiParameters(
    target: PsiElement,
    parameters: SearchSymbolReferenceParameters
  ) : ReferencesSearch.SearchParameters(
    target,
    parameters.originalSearchScope,
    parameters.isIgnoreUseScope
  )
}
