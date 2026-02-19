// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.util.Query
import com.intellij.util.SmartList

/**
 * @param B base type
 * @param R result type
 */
internal typealias Transformation<B, R> = (B) -> Collection<R>

internal typealias XTransformation<B, R> = Transformation<B, XResult<R>>

private object IdTransformation : XTransformation<Any, Any> {
  override fun invoke(e: Any): Collection<XResult<Any>> = listOf(ValueResult(e))
  override fun toString(): String = "ID"
}

@Suppress("UNCHECKED_CAST")
internal fun <R> idTransform(): XTransformation<R, R> = IdTransformation as XTransformation<R, R>

internal fun <B, R> xValueTransform(transformation: Transformation<B, R>): XTransformation<B, R> {
  return { baseValue ->
    transformation(baseValue).mapTo(SmartList(), ::ValueResult)
  }
}

internal fun <B, R> xQueryTransform(subQueries: Transformation<B, Query<out R>>): XTransformation<B, R> {
  return { baseValue ->
    subQueries(baseValue).mapTo(SmartList(), ::QueryResult)
  }
}

/**
 * (>=>) :: (b -> x i) -> (i -> x r) -> (b -> x r)
 */
internal fun <B, I, R> XTransformation<B, I>.karasique(next: XTransformation<I, R>): XTransformation<B, R> {
  @Suppress("UNCHECKED_CAST")
  return when {
    this === IdTransformation -> next as XTransformation<B, R>
    next === IdTransformation -> this as XTransformation<B, R>
    else -> { baseValue: B ->
      this@karasique(baseValue).flatMapTo(SmartList()) { intermediateResult: XResult<I> ->
        intermediateResult.transform(next)
      }
    }
  }
}
