// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.SymbolReference
import com.intellij.model.search.SearchSymbolReferenceParameters
import com.intellij.model.search.TextOccurrence
import com.intellij.psi.impl.search.Transformation
import com.intellij.psi.impl.search.bind
import com.intellij.psi.impl.search.idTransform
import com.intellij.psi.impl.search.transform
import com.intellij.util.Processor
import com.intellij.util.Query
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

  fun run(consumer: (Query<out R>) -> Unit) {
    queryRequest.process(Processor {
      subqueries.apply(it).forEach(consumer)
      true
    })
  }
}

class ParamsRequest<R>(val params: SearchSymbolReferenceParameters, val transformation: Transformation<SymbolReference, R>) {

  fun <T> apply(transformation: Transformation<R, T>): ParamsRequest<T> {
    return ParamsRequest(params, this.transformation.bind(transformation))
  }
}

internal data class WordRequest<R>(val searchWordRequest: SearchWordRequest, val transformation: Transformation<TextOccurrence, R>) {

  fun <T> apply(transformation: Transformation<R, T>): WordRequest<T> {
    return WordRequest(searchWordRequest, this.transformation.bind(transformation))
  }
}

internal fun <T> decompose(query: Query<T>): FlatRequests<T> {
  return when (query) {
    is DecomposableQuery -> query.decompose()
    else -> FlatRequests(myQueryRequests = listOf(QueryRequest(query, idTransform())))
  }
}
