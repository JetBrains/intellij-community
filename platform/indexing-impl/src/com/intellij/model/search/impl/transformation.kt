// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.util.Processor
import com.intellij.util.Query
import com.intellij.util.containers.ContainerUtil
import java.util.function.Function
import java.util.function.Predicate

/**
 * @param B base type
 * @param R result type
 */
internal typealias Transformation<B, R> = (B) -> Collection<R>

internal typealias SubQueries<B, R> = Transformation<B, Query<out R>>

private object IdTransformation : Transformation<Any, Any> {
  override fun invoke(e: Any): Collection<Any> = listOf(e)
  override fun toString(): String = "ID"
}

@Suppress("UNCHECKED_CAST")
fun <R> idTransform(): Transformation<R, R> = IdTransformation as Transformation<R, R>

@Suppress("UNCHECKED_CAST")
fun <B, I, R> Transformation<B, I>.andThen(other: Transformation<I, R>): Transformation<B, R> = when {
  this === IdTransformation -> other as Transformation<B, R>
  other === IdTransformation -> this as Transformation<B, R>
  else -> { base: B ->
    invoke(base).flatMap(other)
  }
}

/**
 * `(b -> q i) -> (i -> r) -> (b -> q r)`
 */
fun <B, I, R> SubQueries<B, I>.mapInner(transformation: Transformation<I, R>): SubQueries<B, R> {
  if (transformation == IdTransformation) {
    @Suppress("UNCHECKED_CAST")
    return this as SubQueries<B, R>
  }
  return { baseElement: B ->
    val intermediateQueries: Collection<Query<out I>> = this@mapInner(baseElement)
    intermediateQueries.map { intermediateQuery: Query<out I> ->
      TransformingQuery(intermediateQuery, transformation)
    }
  }
}

/**
 * `(b -> q i) -> (i -> q r) -> (b -> q r)`
 */
fun <B, I, R> SubQueries<B, I>.flatMapInner(next: SubQueries<I, R>): SubQueries<B, R> {
  return { baseElement: B ->
    val intermediateQueries: Collection<Query<out I>> = this@flatMapInner(baseElement)
    intermediateQueries.map { intermediateQuery: Query<out I> ->
      LayeredQuery(intermediateQuery, next)
    }
  }
}

fun <R> filtering(predicate: Predicate<in R>): Transformation<R, R> = { element: R ->
  if (predicate.test(element)) {
    listOf(element)
  }
  else {
    emptyList()
  }
}

fun <B, R> mapping(f: Function<in B, out R>): Transformation<B, R> = { base: B ->
  listOf(f.apply(base))
}

fun <B, R> Transformation<B, R>.adaptProcessor(resultProcessor: Processor<in R>): Processor<in B> = Processor { baseElement: B ->
  val resultElements: Collection<R> = this@adaptProcessor(baseElement)
  ContainerUtil.process(resultElements, resultProcessor)
}

internal fun <B, R> transformingQuery(baseQuery: Query<out B>, transformation: Transformation<B, R>): Query<out R> {
  if (transformation === IdTransformation) {
    @Suppress("UNCHECKED_CAST")
    return baseQuery as Query<out R>
  }
  else {
    return TransformingQuery(baseQuery, transformation)
  }
}
