// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.lang.LanguageMatcher
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.SearchParameters
import com.intellij.psi.impl.search.OccurrenceProcessor
import com.intellij.psi.impl.search.WordRequestInfoImpl
import com.intellij.util.Processor
import com.intellij.util.Query
import com.intellij.util.containers.ContainerUtil

internal fun <T> decompose(query: Query<T>): Requests<T> {
  return if (query is DecomposableQuery) {
    query.decompose()
  }
  else {
    Requests(queryRequests = listOf(QueryRequest(query, idTransform())))
  }
}

internal class Requests<out R>(
  val parametersRequests: Collection<ParametersRequest<*, R>> = emptyList(),
  val queryRequests: Collection<QueryRequest<*, R>> = emptyList(),
  val wordRequests: Collection<WordRequest<R>> = emptyList()
) {

  companion object {

    private val empty = Requests<Any>()

    @Suppress("UNCHECKED_CAST")
    fun <R> empty() = empty as Requests<R>

    operator fun <T> Requests<T>.plus(other: Requests<T>): Requests<T> {
      return when {
        this === empty -> other
        other === empty -> this
        else -> Requests(
          parametersRequests + other.parametersRequests,
          queryRequests + other.queryRequests,
          wordRequests + other.wordRequests
        )
      }
    }
  }
}

internal class ParametersRequest<B, out R>(
  val params: SearchParameters<out B>,
  val transformation: XTransformation<in B, R>
)

internal class QueryRequest<B, out R>(
  val query: Query<out B>,
  val transformation: XTransformation<in B, R>
)

internal class WordRequest<out R>(
  val searchWordRequest: WordRequestInfoImpl,
  val injectionInfo: InjectionInfo,
  val transformation: XTransformation<LeafOccurrence, R>
)

internal sealed class InjectionInfo {
  object NoInjection : InjectionInfo()
  object IncludeInjections : InjectionInfo()
  class InInjection(val languageInfo: LanguageInfo) : InjectionInfo()
}

internal sealed class LanguageInfo {
  object NoLanguage : LanguageInfo()
  class InLanguage(val matcher: LanguageMatcher) : LanguageInfo()
}

internal fun <B, R> Requests<B>.andThen(transformation: XTransformation<B, R>): Requests<R> = Requests(
  parametersRequests.map { it.andThen(transformation) },
  queryRequests.map { it.andThen(transformation) },
  wordRequests.map { it.andThen(transformation) }
)

private fun <B, R> WordRequest<B>.andThen(t: XTransformation<B, R>): WordRequest<R> {
  return WordRequest(searchWordRequest, injectionInfo, transformation.karasique(t))
}

private fun <B, I, R> QueryRequest<B, I>.andThen(t: XTransformation<I, R>): QueryRequest<B, R> {
  return QueryRequest(query, transformation.karasique(t))
}

private fun <B, I, R> ParametersRequest<B, I>.andThen(t: XTransformation<I, R>): ParametersRequest<B, R> {
  return ParametersRequest(params, transformation.karasique(t))
}

internal fun <R> WordRequest<R>.occurrenceProcessor(processor: Processor<in XResult<R>>): OccurrenceProcessor {
  return { occurrence: LeafOccurrence ->
    val results: Iterable<XResult<R>> = transformation(occurrence)
    ContainerUtil.process(results, processor)
  }
}
