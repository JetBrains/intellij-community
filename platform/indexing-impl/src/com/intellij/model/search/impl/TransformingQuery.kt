// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.psi.impl.search.Transformation
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor
import com.intellij.util.Query
import com.intellij.util.containers.ContainerUtil

internal class TransformingQuery<B, R>(
  val baseQuery: Query<B>,
  private val transformation: Transformation<B, R>
) : AbstractQuery<R>(), DecomposableQuery<R> {

  override fun processResults(consumer: Processor<in R>): Boolean {
    return baseQuery.forEach(Processor { b ->
      ContainerUtil.process(transformation.apply(b), consumer)
    })
  }

  override fun decompose(): FlatRequests<R> {
    val flatBase = decompose(baseQuery)
    return flatBase.apply(transformation)
  }
}
