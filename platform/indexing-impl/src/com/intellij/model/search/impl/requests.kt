// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.search.SearchParameters
import com.intellij.model.search.TextOccurrence
import com.intellij.psi.impl.search.*
import com.intellij.util.Processor
import com.intellij.util.Query
import java.util.function.Function

typealias Subqueries<B, R> = Function<in B, out Collection<Query<out R>>>

internal class QueryRequest<I, O>(val query: Query<I>, val transformation: Transformation<I, O>) {

  fun <T> apply(transformation: Transformation<O, T>): QueryRequest<I, T> {
    return QueryRequest(query, this.transformation.bind(transformation))
  }

  fun <T> layer(subqueries: Subqueries<O, T>): SubqueryRequest<I, *, T> {
    return SubqueryRequest(this, subqueries)
  }

  fun process(processor: Processor<in O>): Boolean = query.forEach(processor.transform(transformation))
}

internal class SubqueryRequest<B, I, R>(private val queryRequest: QueryRequest<B, I>, private val subqueries: Subqueries<I, R>) {

  fun run(consumer: (Query<out R>) -> Unit) {
    queryRequest.process(Processor {
      subqueries.apply(it).forEach(consumer)
      true
    })
  }
}

internal class ParametersRequest<R, O>(val params: SearchParameters<R>, val transformation: Transformation<R, O>) {

  fun <T> apply(transformation: Transformation<O, T>): ParametersRequest<R, T> {
    return ParametersRequest(params, this.transformation.bind(transformation))
  }
}

internal data class WordRequest<R>(
  val searchWordRequest: SearchWordRequest,
  val transformation: Transformation<TextOccurrence, R>
) {

  fun <T> apply(transformation: Transformation<R, T>): WordRequest<T> {
    return WordRequest(searchWordRequest, this.transformation.bind(transformation))
  }
}

internal fun <T> decompose(query: Query<T>): FlatRequests<T> {
  return when (query) {
    is DecomposableQuery -> query.decompose()
    else -> FlatRequests(queryRequests = listOf(QueryRequest(query, idTransform())))
  }
}
