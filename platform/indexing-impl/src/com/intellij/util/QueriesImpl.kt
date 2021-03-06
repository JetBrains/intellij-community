// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.model.search.impl.XQuery
import com.intellij.model.search.impl.xQueryTransform
import com.intellij.model.search.impl.xValueTransform
import java.util.function.Function

internal class QueriesImpl : Queries() {

  override fun <I, O> transforming(base: Query<out I>, transformation: Function<in I, out Collection<O>>): Query<O> {
    return XQuery(base, xValueTransform(transformation::apply))
  }

  override fun <I, O> flatMapping(base: Query<out I>, mapper: Function<in I, out Query<out O>>): Query<O> {
    return XQuery(base, xQueryTransform { baseValue: I ->
      listOf(mapper.apply(baseValue))
    })
  }
}
