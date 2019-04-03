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
import com.intellij.util.LayeredQuery
import com.intellij.util.Processor
import com.intellij.util.Query
import com.intellij.util.TransformingQuery
import java.util.*
import java.util.function.Function

typealias Subqueries<B, R> = Function<in B, out Collection<Query<out R>>>

class QueryRequest<B, R>(val query: Query<B>, val transformation: Transformation<B, R>) {

  fun <T> apply(transformation: Transformation<R, T>): QueryRequest<B, T> {
    return QueryRequest(query, this.transformation.bind(transformation))
  }

  fun <T> layer(subqueries: Subqueries<R, T>): SubqueryRequest<B, *, T> {
    return SubqueryRequest(this, subqueries)
  }

  fun process(processor: Processor<in R>): Boolean = query.forEach(processor.transform(transformation))
}

class SubqueryRequest<B, I, R>(val queryRequest: QueryRequest<B, I>, val subqueries: Subqueries<I, R>) {

  fun <T> apply(transformation: Transformation<R, T>): SubqueryRequest<B, *, T> = TODO()

  fun run(consumer: (Query<out R>) -> Unit) {
    queryRequest.process(Processor {
      subqueries.apply(it).forEach(consumer)
      true
    })
  }
}

class ParamsRequest<R>(val params: SymbolReferenceSearchParameters, val transformation: Transformation<SymbolReference, R>) {

  fun <T> apply(transformation: Transformation<R, T>): ParamsRequest<T> {
    return ParamsRequest(params, this.transformation.bind(transformation))
  }
}

data class WordRequest<R>(val searchWordRequest: SearchWordRequest, val transformation: Transformation<TextOccurrence, R>) {

  fun <T> apply(transformation: Transformation<R, T>): WordRequest<T> {
    return WordRequest(searchWordRequest, this.transformation.bind(transformation))
  }
}

internal class FlatRequests<T>(
  val myQueryRequests: Collection<QueryRequest<*, T>> = emptyList(),
  val myParamsRequests: Collection<ParamsRequest<T>> = emptyList(),
  val myWordRequests: Collection<WordRequest<T>> = emptyList(),
  val mySubQueryRequests: Collection<SubqueryRequest<*, *, T>> = emptyList()
) {

  internal fun <R> apply(transformation: Transformation<T, R>): FlatRequests<R> = FlatRequests(
    myQueryRequests.map { it.apply(transformation) },
    myParamsRequests.map { it.apply(transformation) },
    myWordRequests.map { it.apply(transformation) },
    mySubQueryRequests.map { it.apply(transformation) }
  )

  internal fun <R> layer(subqueries: Subqueries<T, R>): FlatRequests<R> {
    require(myParamsRequests.isEmpty())
    require(myWordRequests.isEmpty())
    require(mySubQueryRequests.isEmpty())
    return FlatRequests(
      mySubQueryRequests = myQueryRequests.map { it.layer(subqueries) }
    )
  }
}

internal fun <T> flatten(query: Query<T>): FlatRequests<T> {
  return when (query) {
    is LayeredQuery<*, *> -> flatten(query as LayeredQuery<*, T>)
    is TransformingQuery<*, *> -> flatten(query as TransformingQuery<*, T>)
    is SymbolReferenceQuery -> flatten(query)
    is SearchWordQuery -> flatten(query)
    else -> FlatRequests(myQueryRequests = listOf(QueryRequest(query, idTransform())))
  }
}

private fun <B, R> flatten(query: LayeredQuery<B, R>): FlatRequests<R> {
  val flatBase: FlatRequests<B> = flatten(query.baseQuery)
  val subqueries: Subqueries<B, R> = query.subqueries
  return flatBase.layer(subqueries)
}

private fun <B, R> flatten(query: TransformingQuery<B, R>): FlatRequests<R> {
  return flatten(query.baseQuery).apply(query.transform)
}

private fun <T> flatten(query: SymbolReferenceQuery): FlatRequests<T> {
  @Suppress("UNCHECKED_CAST")
  return flattenInner(query) as FlatRequests<T>
}

private fun flattenInner(query: SymbolReferenceQuery): FlatRequests<SymbolReference> {
  val queryRequest: QueryRequest<*, SymbolReference> = QueryRequest(query.baseQuery, idTransform())
  val parametersRequest: ParamsRequest<SymbolReference> = ParamsRequest(query.parameters, idTransform())
  return FlatRequests(myQueryRequests = listOf(queryRequest), myParamsRequests = listOf(parametersRequest))
}

private fun <T> flatten(query: SearchWordQuery): FlatRequests<T> {
  @Suppress("UNCHECKED_CAST")
  return flattenInner(query) as FlatRequests<T>
}

private fun flattenInner(query: SearchWordQuery): FlatRequests<TextOccurrence> {
  val words: Collection<SearchWordRequest> = createRequests(query.parameters)
  val wordRequests: Collection<WordRequest<TextOccurrence>> = words.map { WordRequest(it, idTransform()) }
  return FlatRequests(myWordRequests = wordRequests)
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
      val codeScope = searchScope.intersectWith(restrictedCodeUsageSearchScope)
      val codeRequest = SearchWordRequest(word, codeScope, caseSensitive, IN_CODE.mask, null)
      val nonCodeRequest = SearchWordRequest(word, searchScope, caseSensitive, mask(contexts - IN_CODE), null)
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
