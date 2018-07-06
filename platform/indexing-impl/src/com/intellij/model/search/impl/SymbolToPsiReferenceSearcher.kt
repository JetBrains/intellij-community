// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.Symbol
import com.intellij.model.SymbolReference
import com.intellij.model.SymbolService
import com.intellij.model.search.DefaultSymbolReferenceSearchParameters
import com.intellij.model.search.SymbolReferenceSearch
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.QuerySearchRequest
import com.intellij.psi.search.SearchRequestCollector
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.CustomProcessorQuery
import com.intellij.util.PairProcessor
import com.intellij.util.Processor

/**
 * Includes [SymbolReferenceSearch] results into [ReferencesSearch] results.
 *
 * @see PsiToSymbolSearchRequestor
 */
class SymbolToPsiReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(false) {

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    if (queryParameters is PsiToSymbolSearchRequestor.SymbolToPsiParameters) {
      // search started from SymbolReferenceSearch
      // -> PsiToSymbolSearchRequestor queries ReferenceSearch with SymbolToPsiParameters
      // -> don't query SymbolReferenceSearch again because we started from there
      return
    }
    val modelElement = SymbolService.adaptPsiElement(queryParameters.elementToSearch)
    val modelParameters = PsiToSymbolParameters(modelElement, queryParameters)
    val modelQuery = SymbolReferenceSearch.search(modelParameters)
    val psiQuery = CustomProcessorQuery(modelQuery, this::adaptProcessor)
    queryParameters.optimizer.apply {
      val nested = SearchRequestCollector(searchSession)
      val request = QuerySearchRequest(psiQuery, nested, false, PairProcessor { ref, _ -> consumer.process(ref) })
      searchQuery(request)
    }
  }

  private fun adaptProcessor(psiProcessor: Processor<in PsiReference>): Processor<in SymbolReference> {
    return Processor { modelReference ->
      if (modelReference is PsiReference) {
        psiProcessor.process(modelReference)
      }
      else {
        TODO("Found reference: $modelReference, class: ${modelReference.javaClass}")
      }
    }
  }

  internal class PsiToSymbolParameters(
    target: Symbol,
    psiParameters: ReferencesSearch.SearchParameters
  ) : DefaultSymbolReferenceSearchParameters(
    psiParameters.project,
    target,
    psiParameters.scopeDeterminedByUser,
    psiParameters.isIgnoreAccessScope
  )
}
