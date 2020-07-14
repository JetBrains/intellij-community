// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.lang.Language
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceProviderBean
import com.intellij.model.psi.impl.ReferenceProviders
import com.intellij.model.search.*
import com.intellij.model.search.SearchContext.IN_CODE
import com.intellij.model.search.SearchContext.IN_CODE_HOSTS
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
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
      val language: Language = searcher.referencingLanguage
      val searchScope: SearchScope = request.searchScope?.intersectWith(inputScope) ?: inputScope
      val injectionSearchScope: SearchScope = request.injectionSearchScope?.intersectWith(inputScope) ?: inputScope
      val mapper: LeafOccurrenceMapper<PsiSymbolReference> = CodeReferenceMapper(targetPointer, searcher)
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

    val mapper = ExternalReferenceMapper(targetPointer)
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
}
