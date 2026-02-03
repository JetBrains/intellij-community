// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.util.Processor
import com.intellij.util.Query

internal class XQuery<B, R>(
  private val baseQuery: Query<out B>,
  private val transformation: XTransformation<B, R>
) : AbstractDecomposableQuery<R>() {

  override fun processResults(consumer: Processor<in R>): Boolean {
    return baseQuery.forEach(Processor { baseValue ->
      for (result: XResult<R> in transformation(baseValue)) {
        if (!result.process(consumer)) {
          return@Processor false
        }
      }
      true
    })
  }

  override fun decompose(): Requests<R> {
    return decompose(baseQuery).andThen(transformation)
  }
}
