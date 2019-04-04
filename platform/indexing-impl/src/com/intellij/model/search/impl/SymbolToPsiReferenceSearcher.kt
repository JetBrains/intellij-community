// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.Symbol
import com.intellij.model.SymbolReference
import com.intellij.model.SymbolService
import com.intellij.model.search.SearchService
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiReference
import com.intellij.psi.search.QuerySearchRequest
import com.intellij.psi.search.SearchRequestCollector
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch.SearchParameters
import com.intellij.util.CustomProcessorQuery
import com.intellij.util.PairProcessor
import com.intellij.util.Processor

class SymbolToPsiReferenceSearcher : QueryExecutorBase<PsiReference, SearchParameters>(false) {

  override fun processQuery(queryParameters: SearchParameters, consumer: Processor<in PsiReference>) {
    if (!Registry.`is`("ide.symbol.reference.search")) return
    if (queryParameters is PsiToSymbolSearchRequestor.SymbolToPsiParameters) {
      // search started from SymbolReferenceSearch
      // -> PsiToSymbolSearchRequestor queries ReferenceSearch with SymbolToPsiParameters
      // -> don't query SymbolReferenceSearch again because we started from there
      return
    }
    val symbol = SymbolService.adaptPsiElement(queryParameters.elementToSearch)
    val symbolParameters = PsiToSymbolParameters(symbol, queryParameters)
    val symbolQuery = SearchService.getInstance().searchTarget(symbolParameters)
    val psiQuery = CustomProcessorQuery(symbolQuery, this::adaptProcessor)
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
    private val target: Symbol,
    private val psiParameters: SearchParameters
  ) : SearchSymbolReferenceParametersBase() {
    override fun getProject(): Project = psiParameters.project
    override fun getTarget(): Symbol = target
    override fun getOriginalSearchScope(): SearchScope = psiParameters.scopeDeterminedByUser
    override fun isIgnoreUseScope(): Boolean = psiParameters.isIgnoreAccessScope
  }
}
