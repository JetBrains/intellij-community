// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.psi.impl.search.Transformation

internal class FlatRequests<T>(
  val queryRequests: Collection<QueryRequest<*, T>> = emptyList(),
  val parametersRequests: Collection<ParametersRequest<*, T>> = emptyList(),
  val wordRequests: Collection<WordRequest<T>> = emptyList(),
  val subqueryRequests: Collection<SubqueryRequest<*, *, T>> = emptyList()
) {

  internal fun <R> apply(transformation: Transformation<T, R>): FlatRequests<R> {
    require(subqueryRequests.isEmpty())
    return FlatRequests(
      queryRequests.map { it.apply(transformation) },
      parametersRequests.map { it.apply(transformation) },
      wordRequests.map { it.apply(transformation) }
    )
  }

  internal fun <R> layer(subqueries: Subqueries<T, R>): FlatRequests<R> {
    require(parametersRequests.isEmpty())
    require(wordRequests.isEmpty())
    require(subqueryRequests.isEmpty())
    return FlatRequests(
      subqueryRequests = queryRequests.map { it.layer(subqueries) }
    )
  }
}
