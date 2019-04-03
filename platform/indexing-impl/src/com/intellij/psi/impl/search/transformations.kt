// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import java.util.function.Function

/**
 * @param B base type
 * @param R result type
 */
typealias Transformation<B, R> = Function<in B, out Collection<R>>

private val idTransformation: Transformation<Any, Any> = object : Function<Any, Collection<Any>> {
  override fun apply(t: Any): Collection<Any> = listOf(t)
  override fun toString(): String = "ID"
}

@Suppress("UNCHECKED_CAST")
fun <R> idTransform(): Transformation<R, R> = idTransformation as Transformation<R, R>

fun <B, I, R> Transformation<B, I>.bind(other: Transformation<I, R>): Transformation<B, R> {
  @Suppress("UNCHECKED_CAST")
  return when {
    this === idTransformation -> other as Transformation<B, R>
    other === idTransformation -> this as Transformation<B, R>
    else -> Transformation {
      apply(it).flatMap(other::apply)
    }
  }
}

fun <B, R> Processor<in R>.transform(transformation: Transformation<B, R>): Processor<in B> {
  return Processor { t ->
    ContainerUtil.process(transformation.apply(t), this)
  }
}
