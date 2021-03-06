// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.lang.Language
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.*
import com.intellij.model.psi.impl.ReferenceProviders
import com.intellij.model.search.*
import com.intellij.model.search.SearchContext.IN_CODE
import com.intellij.model.search.SearchContext.IN_CODE_HOSTS
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.walkUp
import com.intellij.util.Query

internal class DefaultReferenceSearcher : PsiSymbolReferenceSearcher {

  override fun collectSearchRequests(parameters: PsiSymbolReferenceSearchParameters): Collection<Query<out PsiSymbolReference>> {
    val project: Project = parameters.project
    val inputScope: SearchScope = parameters.searchScope
    val target: Symbol = parameters.symbol
    val targetPointer: Pointer<out Symbol> = target.createPointer()
    val service = SearchService.getInstance()

    val result = mutableListOf<Query<out PsiSymbolReference>>()

    for (searcher: CodeReferenceSearcher in CodeReferenceSearcher.EP_NAME.extensionList) {
      val request: SearchRequest = searcher.getSearchRequest(project, target) ?: continue
      val language: Language = searcher.getReferencingLanguage(target)
      val searchScope: SearchScope = request.searchScope?.intersectWith(inputScope) ?: inputScope
      val injectionSearchScope: SearchScope = request.injectionSearchScope?.intersectWith(inputScope) ?: inputScope
      val mapper: LeafOccurrenceMapper<PsiSymbolReference> = LeafOccurrenceMapper.withPointer(targetPointer, searcher::getReferences)
      val builder: SearchWordQueryBuilder = service.searchWord(project, request.searchString)
      result += builder
        .inContexts(IN_CODE)
        .inScope(searchScope)
        .inFilesWithLanguage(language)
        .buildQuery(mapper)
      result += builder
        .inContexts(IN_CODE_HOSTS)
        .inScope(injectionSearchScope)
        .inInjections(language)
        .buildQuery(mapper)
    }

    val mapper = LeafOccurrenceMapper.withPointer(targetPointer, ::externalReferences)
    for (providerBean: PsiSymbolReferenceProviderBean in ReferenceProviders.byTargetClass(target.javaClass)) {
      val requests = providerBean.instance.getSearchRequests(project, target)
      for (request: SearchRequest in requests) {
        val language: Language = providerBean.getHostLanguage()
        val searchScope: SearchScope = request.searchScope?.intersectWith(inputScope) ?: inputScope
        val injectionSearchScope: SearchScope = request.injectionSearchScope?.intersectWith(inputScope) ?: inputScope
        val builder: SearchWordQueryBuilder = service.searchWord(project, request.searchString)
          .inContexts(IN_CODE_HOSTS)
        result += builder
          .inScope(searchScope)
          .inFilesWithLanguage(language)
          .buildQuery(mapper)
        result += builder
          .inScope(injectionSearchScope)
          .inInjections(language)
          .buildQuery(mapper)
      }
    }

    return result
  }

  private fun externalReferences(target: Symbol, occurrence: LeafOccurrence): Collection<PsiSymbolReference> {
    val (scope, start, offsetInStart) = occurrence
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
