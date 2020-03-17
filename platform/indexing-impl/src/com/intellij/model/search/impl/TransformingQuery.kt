// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.util.Processor
import com.intellij.util.Query

internal class TransformingQuery<B, R>(
  private val baseQuery: Query<out B>,
  private val transformation: Transformation<B, R>
) : AbstractDecomposableQuery<R>() {

  override fun processResults(consumer: Processor<in R>): Boolean {
    return baseQuery.forEach(transformation.adaptProcessor(consumer))
  }

  override fun decompose(): PrimitiveRequests<R> {
    val flatBase: PrimitiveRequests<B> = decompose(baseQuery)
    return flatBase.apply(transformation)
  }
}
