// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.Symbol
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchScopeOptimizer
import com.intellij.model.search.SearchWordParameters
import com.intellij.model.search.TextOccurrence
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.search.idTransform
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.util.Processor
import java.util.*

internal class SearchWordQueryImpl(
  private val myParameters: SearchWordParameters
) : AbstractDecomposableQuery<TextOccurrence>() {

  override fun processResults(consumer: Processor<in TextOccurrence>): Boolean {
    TODO()
  }

  override fun decompose(): FlatRequests<TextOccurrence> {
    val words: Collection<SearchWordRequest> = createRequests(myParameters)
    val wordRequests: Collection<WordRequest<TextOccurrence>> = words.map { WordRequest(it, idTransform()) }
    return FlatRequests(myWordRequests = wordRequests)
  }
}

private fun createRequests(parameters: SearchWordParameters): Collection<SearchWordRequest> {
  val searchScope = parameters.searchScope
  if (!makesSenseToSearch(searchScope)) {
    return emptyList()
  }

  val word = parameters.word
  val targetHint = parameters.targetHint
  val contexts = parameters.searchContexts
  val contextMask = mask(contexts)
  val caseSensitive = parameters.isCaseSensitive

  if (targetHint != null && searchScope is GlobalSearchScope && SearchContext.IN_CODE in contexts) {
    val project = parameters.project
    val restrictedCodeUsageSearchScope = getRestrictedScope(project, targetHint)
    if (restrictedCodeUsageSearchScope != null) {
      val codeScope = searchScope.intersectWith(restrictedCodeUsageSearchScope)
      val codeRequest = SearchWordRequest(word, codeScope, caseSensitive, SearchContext.IN_CODE.mask, null)
      val nonCodeRequest = SearchWordRequest(word, searchScope, caseSensitive,
                                             mask(contexts - SearchContext.IN_CODE), null)
      return Arrays.asList(codeRequest, nonCodeRequest)
    }
  }
  return setOf(SearchWordRequest(word, searchScope, caseSensitive, contextMask, null))
}

private fun makesSenseToSearch(searchScope: SearchScope): Boolean {
  return if (searchScope is LocalSearchScope && searchScope.scope.isEmpty()) {
    false
  }
  else {
    searchScope !== GlobalSearchScope.EMPTY_SCOPE
  }
}

private fun mask(contexts: Set<SearchContext>): Short {
  return contexts
    .map { context -> context.mask.toInt() }
    .reduce { a, b -> a or b }
    .toShort()
}

private fun getRestrictedScope(project: Project, symbol: Symbol): SearchScope? {
  return runReadAction {
    getRestrictedScope(SearchScopeOptimizer.CODE_USE_SCOPE_EP.extensions, project, symbol)
  }
}

private fun getRestrictedScope(optimizers: Array<SearchScopeOptimizer>, project: Project, symbol: Symbol): SearchScope? {
  return optimizers.map {
    ProgressManager.checkCanceled()
    it.getRestrictedUseScope(project, symbol)
  }.fold(null, ::intersectNullable)
}

private fun intersectNullable(scope1: SearchScope?, scope2: SearchScope?): SearchScope? {
  if (scope1 == null) return scope2
  return if (scope2 == null) scope1 else scope1.intersectWith(scope2)
}
