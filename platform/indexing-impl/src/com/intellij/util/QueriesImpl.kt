// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.model.search.impl.LayeredQuery
import com.intellij.model.search.impl.TransformingQuery
import com.intellij.psi.impl.search.filtering
import com.intellij.psi.impl.search.mapping
import java.util.function.Function
import java.util.function.Predicate

internal class QueriesImpl : Queries() {

  override fun <I, O> map(base: Query<out I>, mapper: Function<in I, out O>): Query<O> {
    return TransformingQuery(base, mapping(mapper))
  }

  override fun <T> filter(base: Query<T>, predicate: Predicate<in T>): Query<T> {
    return TransformingQuery(base, filtering(predicate))
  }

  override fun <I, O> flatMap(base: Query<out I>, mapper: Function<in I, out Query<out O>>): Query<O> {
    return LayeredQuery(base, mapping(mapper))
  }
}
