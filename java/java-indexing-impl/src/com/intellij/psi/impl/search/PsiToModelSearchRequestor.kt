// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.ModelReference
import com.intellij.model.ModelService
import com.intellij.model.search.ModelReferenceSearchParameters
import com.intellij.model.search.SearchRequestCollector
import com.intellij.model.search.SearchRequestor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.CustomProcessorQuery
import com.intellij.util.Processor
import com.intellij.util.Query

/**
 * Includes [ReferencesSearch] results into [com.intellij.model.search.ModelReferenceSearch] results.
 *
 * @see ModelToPsiReferenceSearcher
 */
class PsiToModelSearchRequestor : SearchRequestor {

  override fun collectSearchRequests(collector: SearchRequestCollector, parameters: ModelReferenceSearchParameters) {
    if (parameters is ModelToPsiReferenceSearcher.PsiToModelParameters) {
      // search started from ReferencesSearch
      // -> ModelToPsiReferenceSearcher queries ModelReferenceSearch with PsiToModelParameters
      // -> don't query ReferenceSearch again because we started from there
      return
    }
    val psiElement = ModelService.getPsiElement(parameters.target) ?: return
    val psiSearchParameters = ModelToPsiParameters(psiElement, parameters)
    val psiQuery: Query<PsiReference> = ReferencesSearch.search(psiSearchParameters)
    collector.searchSubQuery(CustomProcessorQuery(psiQuery, ::adaptProcessor))
  }

  private fun adaptProcessor(modelProcessor: Processor<in ModelReference>) = Processor<PsiReference>(modelProcessor::process)

  internal class ModelToPsiParameters(
    target: PsiElement,
    parameters: ModelReferenceSearchParameters
  ) : ReferencesSearch.SearchParameters(
    target,
    parameters.originalSearchScope,
    parameters.isIgnoreAccessScope
  )
}
