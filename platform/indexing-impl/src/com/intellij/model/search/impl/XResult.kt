// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.util.Processor
import com.intellij.util.Query

/**
 * Value of type [X] or [Query] of values of type [X].
 */
internal sealed class XResult<out X> {

  abstract fun process(processor: Processor<in X>): Boolean

  abstract fun <R> transform(transformation: XTransformation<X, R>): Collection<XResult<R>>
}

internal class ValueResult<X>(val value: X) : XResult<X>() {

  override fun process(processor: Processor<in X>): Boolean {
    return processor.process(value)
  }

  override fun <R> transform(transformation: XTransformation<X, R>): Collection<XResult<R>> {
    return transformation(value)
  }
}

internal class QueryResult<X>(val query: Query<out X>) : XResult<X>() {

  override fun process(processor: Processor<in X>): Boolean {
    return query.forEach(processor)
  }

  override fun <R> transform(transformation: XTransformation<X, R>): Collection<XResult<R>> {
    return listOf(QueryResult(XQuery(query, transformation)))
  }
}
