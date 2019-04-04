// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.psi.impl.search.filtering
import com.intellij.psi.impl.search.mapping
import com.intellij.util.AbstractQuery
import com.intellij.util.Query
import java.util.function.Function
import java.util.function.Predicate

internal abstract class AbstractDecomposableQuery<T> : AbstractQuery<T>(), DecomposableQuery<T> {

  override fun <R> map(mapper: Function<in T, out R>): Query<R> {
    return TransformingQuery(this, mapping(mapper))
  }

  override fun filter(predicate: Predicate<in T>): Query<T> {
    return TransformingQuery(this, filtering(predicate))
  }

  override fun <R> flatMap(subquery: Function<in T, out Query<out R>>): Query<R> {
    return LayeredQuery(this, mapping(subquery))
  }
}
