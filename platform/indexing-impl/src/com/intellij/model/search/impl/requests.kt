// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.lang.LanguageMatcher
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchParameters
import com.intellij.model.search.impl.Requests.Companion.plus
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.search.LeafOccurrence
import com.intellij.psi.impl.search.OccurrenceProcessor
import com.intellij.psi.impl.search.WordRequestInfoImpl
import com.intellij.util.Processor
import com.intellij.util.Query
import com.intellij.util.containers.ContainerUtil

internal fun <T> decompose(query: Query<T>): PrimitiveRequests<T> {
  return if (query is DecomposableQuery) {
    query.decompose()
  }
  else {
    PrimitiveRequests(Requests(queryRequests = listOf(QueryRequest(query, idTransform()))))
  }
}

internal class PrimitiveRequests<out R>(
  val resultRequests: Requests<R> = Requests.empty(),
  val subQueryRequests: Requests<Query<out R>> = Requests.empty()
) {

  fun <T> apply(transformation: Transformation<R, T>): PrimitiveRequests<T> {
    if (this === empty) {
      return empty()
    }
    else {
      return PrimitiveRequests(
        resultRequests.andThen(transformation),
        subQueryRequests.mapInner(transformation)
      )
    }
  }

  fun <T> layer(subQueries: SubQueries<R, T>): PrimitiveRequests<T> {
    if (this === empty) {
      return empty()
    }
    else {
      return PrimitiveRequests(
        subQueryRequests = resultRequests.andThen(subQueries) + subQueryRequests.flatMapInner(subQueries)
      )
    }
  }

  companion object {

    private val empty = PrimitiveRequests<Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> empty(): PrimitiveRequests<T> = empty as PrimitiveRequests<T>

    operator fun <R> PrimitiveRequests<R>.plus(other: PrimitiveRequests<R>): PrimitiveRequests<R> = when {
      this === empty -> other
      other === empty -> this
      else -> PrimitiveRequests(
        resultRequests + other.resultRequests,
        subQueryRequests + other.subQueryRequests
      )
    }
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
  val transformation: Transformation<in B, R>
)

internal class QueryRequest<B, out R>(
  val query: Query<out B>,
  val transformation: Transformation<in B, R>
)

internal class WordRequest<out R>(
  val searchWordRequest: WordRequestInfoImpl,
  val injectionInfo: InjectionInfo,
  val transformation: Transformation<LeafOccurrence, R>
)

internal sealed class InjectionInfo {
  object NoInjection : InjectionInfo()
  class InInjection(val languageInfo: LanguageInfo) : InjectionInfo()
}

internal sealed class LanguageInfo {
  object NoLanguage : LanguageInfo()
  class InLanguage(val matcher: LanguageMatcher) : LanguageInfo()
}

private fun <B, R> Requests<B>.andThen(transformation: Transformation<B, R>): Requests<R> = Requests(
  parametersRequests.map { it.andThen(transformation) },
  queryRequests.map { it.andThen(transformation) },
  wordRequests.map { it.andThen(transformation) }
)

private fun <B, R> Requests<Query<out B>>.mapInner(transformation: Transformation<B, R>): Requests<Query<out R>> = Requests(
  parametersRequests.map { it.mapInner(transformation) },
  queryRequests.map { it.mapInner(transformation) },
  wordRequests.map { it.mapInner(transformation) }
)

private fun <B, R> Requests<Query<out B>>.flatMapInner(subQueries: SubQueries<B, R>): Requests<Query<out R>> = Requests(
  parametersRequests.map { it.flatMapInner(subQueries) },
  queryRequests.map { it.flatMapInner(subQueries) },
  wordRequests.map { it.flatMapInner(subQueries) }
)

private fun <B, I, R> ParametersRequest<B, I>.andThen(t: Transformation<I, R>): ParametersRequest<B, R> {
  return ParametersRequest(params, transformation.andThen(t))
}

private fun <B, I, R> ParametersRequest<B, Query<out I>>.mapInner(t: Transformation<I, R>): ParametersRequest<B, Query<out R>> {
  return ParametersRequest(params, transformation.mapInner(t))
}

private fun <B, I, R> ParametersRequest<B, Query<out I>>.flatMapInner(subQueries: SubQueries<I, R>): ParametersRequest<B, Query<out R>> {
  return ParametersRequest(params, transformation.flatMapInner(subQueries))
}

private fun <B, I, R> QueryRequest<B, I>.andThen(t: Transformation<I, R>): QueryRequest<B, R> {
  return QueryRequest(query, transformation.andThen(t))
}

private fun <B, I, R> QueryRequest<B, Query<out I>>.mapInner(t: Transformation<I, R>): QueryRequest<B, Query<out R>> {
  return QueryRequest(query, transformation.mapInner(t))
}

private fun <B, I, R> QueryRequest<B, Query<out I>>.flatMapInner(subQueries: SubQueries<I, R>): QueryRequest<B, Query<out R>> {
  return QueryRequest(query, transformation.flatMapInner(subQueries))
}

private fun <I, R> WordRequest<I>.andThen(t: Transformation<I, R>): WordRequest<R> {
  return WordRequest(searchWordRequest, injectionInfo, transformation.andThen(t))
}

private fun <I, R> WordRequest<Query<out I>>.mapInner(t: Transformation<I, R>): WordRequest<Query<out R>> {
  return WordRequest(searchWordRequest, injectionInfo, transformation.mapInner(t))
}

private fun <I, R> WordRequest<Query<out I>>.flatMapInner(subQueries: SubQueries<I, R>): WordRequest<Query<out R>> {
  return WordRequest(searchWordRequest, injectionInfo, transformation.flatMapInner(subQueries))
}

internal fun <T> LeafOccurrenceMapper<out T>.asTransformation(): Transformation<LeafOccurrence, T> {
  return { (scope: PsiElement, start: PsiElement, offsetInStart: Int) ->
    this@asTransformation.mapOccurrence(scope, start, offsetInStart)
  }
}

internal fun <R> WordRequest<R>.occurrenceProcessor(processor: Processor<in R>): OccurrenceProcessor {
  return { occurrence: LeafOccurrence ->
    val results: Iterable<R> = transformation(occurrence)
    ContainerUtil.process(results, processor)
  }
}
