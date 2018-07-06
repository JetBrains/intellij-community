// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.SymbolService
import com.intellij.model.search.SearchRequestCollector
import com.intellij.model.search.SearchRequestor
import com.intellij.model.search.SymbolReferenceSearchParameters
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Query

/**
 * Includes [ReferencesSearch] results into [com.intellij.model.search.SymbolReferenceSearch] results.
 *
 * @see SymbolToPsiReferenceSearcher
 */
class PsiToSymbolSearchRequestor : SearchRequestor {

  override fun collectSearchRequests(collector: SearchRequestCollector) {
    val parameters = collector.parameters
    if (parameters is SymbolToPsiReferenceSearcher.PsiToSymbolParameters) {
      // search started from ReferencesSearch
      // -> SymbolToPsiReferenceSearcher queries SymbolReferenceSearch with PsiToSymbolParameters
      // -> don't query ReferenceSearch again because we started from there
      return
    }
    val psiElement = SymbolService.getPsiElement(parameters.target) ?: return
    val psiSearchParameters = SymbolToPsiParameters(psiElement, parameters)
    val psiQuery: Query<PsiReference> = ReferencesSearch.search(psiSearchParameters)
    collector.searchSubQuery(psiQuery)
  }

  internal class SymbolToPsiParameters(
    target: PsiElement,
    parameters: SymbolReferenceSearchParameters
  ) : ReferencesSearch.SearchParameters(
    target,
    parameters.originalSearchScope,
    parameters.isIgnoreAccessScope
  )
}
