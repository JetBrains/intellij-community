// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.Symbol
import com.intellij.model.SymbolReference
import com.intellij.model.search.*
import com.intellij.model.search.SearchContext.IN_CODE
import com.intellij.model.search.SearchScopeOptimizer.CODE_USE_SCOPE_EP
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.util.Query
import com.intellij.util.TransformingQuery
import java.util.*
import kotlin.experimental.xor

class QueryRequest<B, R>(val query: Query<B>, val transform: Transform<B, R>) {
  fun <T> apply(transform: Transform<R, T>): QueryRequest<B, T> {
    return QueryRequest(query, Transform {
      this.transform.apply(it).flatMap(transform::apply)
    })
  }
}

class ParamsRequest<R>(val params: SymbolReferenceSearchParameters, val transform: Transform<SymbolReference, R>) {
  fun <T> apply(transform: Transform<R, T>): ParamsRequest<T> {
    return ParamsRequest(params, Transform {
      this.transform.apply(it).flatMap(transform::apply)
    })
  }
}

data class WordRequest<R>(val searchWordRequest: SearchWordRequest, val transform: Transform<TextOccurrence, R>) {
  fun <T> apply(transform: Transform<R, T>): WordRequest<T> {
    return WordRequest(searchWordRequest, Transform {
      this.transform.apply(it).flatMap(transform::apply)
    })
  }
}


internal class FlatRequests<T>(
  val myQueries: Collection<Query<out T>> = emptyList(),
  val myQueryRequests: Collection<QueryRequest<*, T>> = emptyList(),
  val myParams: Collection<SymbolReferenceSearchParameters> = emptyList(),
  val myParamsRequests: Collection<ParamsRequest<T>> = emptyList(),
  val myWords: Collection<SearchWordRequest> = emptyList(),
  val myWordRequests: Collection<WordRequest<T>> = emptyList()
) {

  internal fun <R> apply(transform: Transform<T, R>): FlatRequests<R> {
    val newQueryRequests = mutableListOf<QueryRequest<*, R>>()
    myQueries.mapTo(newQueryRequests) { QueryRequest(it, transform) }
    myQueryRequests.mapTo(newQueryRequests) { it.apply(transform) }

    require(myParams.isEmpty() || myWords.isEmpty())

    val newParamsRequests = mutableListOf<ParamsRequest<R>>()
    @Suppress("UNCHECKED_CAST")
    myParams.mapTo(newParamsRequests) { ParamsRequest(it, transform as Transform<SymbolReference, R>) }
    myParamsRequests.mapTo(newParamsRequests) { it.apply(transform) }

    val newWordRequests = mutableListOf<WordRequest<R>>()
    @Suppress("UNCHECKED_CAST")
    myWords.mapTo(newWordRequests) { WordRequest(it, transform as Transform<TextOccurrence, R>) }
    myWordRequests.mapTo(newWordRequests) { it.apply(transform) }

    return FlatRequests(
      myQueryRequests = newQueryRequests,
      myParamsRequests = newParamsRequests,
      myWordRequests = newWordRequests
    )
  }
}


internal fun <T> flatten(query: Query<T>): FlatRequests<T> {
  return when (query) {
    is TransformingQuery<*, *> -> flatten(query as TransformingQuery<*, T>)
    is SymbolReferenceQuery -> flatten(query as SymbolReferenceQuery)
    is SearchWordQuery -> FlatRequests(myWords = createRequests(query.parameters))
    else -> FlatRequests(myQueries = listOf(query))
  }
}

private fun <B, R> flatten(query: TransformingQuery<B, R>): FlatRequests<R> {
  return flatten(query.baseQuery).apply(query.transform)
}


@Suppress("UNCHECKED_CAST")
private fun <T> flatten(query: SymbolReferenceQuery): FlatRequests<T> {
  return FlatRequests(myQueries = listOf(query.baseQuery), myParams = listOf(query.parameters)) as FlatRequests<T>
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

  if (targetHint != null && searchScope is GlobalSearchScope && IN_CODE in contexts) {
    val project = parameters.project
    val restrictedCodeUsageSearchScope = getRestrictedScope(project, targetHint)
    if (restrictedCodeUsageSearchScope != null) {
      val nonCodeContextMask = contextMask xor IN_CODE.mask
      val codeScope = searchScope.intersectWith(restrictedCodeUsageSearchScope)
      val codeRequest = SearchWordRequest(word, codeScope, caseSensitive, IN_CODE.mask, null)
      val nonCodeRequest = SearchWordRequest(word, searchScope, caseSensitive, nonCodeContextMask, null)
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
    getRestrictedScope(CODE_USE_SCOPE_EP.extensions, project, symbol)
  }
}
