// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.util.Processor
import com.intellij.util.Query

internal class LayeredQuery<B, R>(
  private val baseQuery: Query<B>,
  private val subqueries: Subqueries<B, R>
) : AbstractDecomposableQuery<R>() {

  override fun processResults(consumer: Processor<in R>): Boolean {
    return baseQuery.forEach(Processor { base ->
      for (subQuery in subqueries.apply(base)) {
        if (!subQuery.forEach(consumer)) {
          return@Processor false
        }
      }
      true
    })
  }

  override fun decompose(): FlatRequests<R> {
    val flatBase: FlatRequests<B> = decompose(baseQuery)
    return flatBase.layer(subqueries)
  }
}
