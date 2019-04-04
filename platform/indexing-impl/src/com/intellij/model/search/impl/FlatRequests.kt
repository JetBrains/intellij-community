// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.psi.impl.search.Transformation

internal class FlatRequests<T>(
  val myQueryRequests: Collection<QueryRequest<*, T>> = emptyList(),
  val myParamsRequests: Collection<ParamsRequest<T>> = emptyList(),
  val myWordRequests: Collection<WordRequest<T>> = emptyList(),
  val mySubQueryRequests: Collection<SubqueryRequest<*, *, T>> = emptyList()
) {

  internal fun <R> apply(transformation: Transformation<T, R>): FlatRequests<R> {
    require(mySubQueryRequests.isEmpty())
    return FlatRequests(
      myQueryRequests.map { it.apply(transformation) },
      myParamsRequests.map { it.apply(transformation) },
      myWordRequests.map { it.apply(transformation) }
    )
  }

  internal fun <R> layer(subqueries: Subqueries<T, R>): FlatRequests<R> {
    require(myParamsRequests.isEmpty())
    require(myWordRequests.isEmpty())
    require(mySubQueryRequests.isEmpty())
    return FlatRequests(
      mySubQueryRequests = myQueryRequests.map { it.layer(subqueries) }
    )
  }
}
