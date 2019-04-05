// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.psi.impl.search.idTransform
import com.intellij.util.Processor
import com.intellij.util.Query

internal class CompositeQuery<T>(private val queries: Collection<Query<T>>) : AbstractDecomposableQuery<T>() {

  constructor(vararg queries: Query<T>) : this(listOf(*queries))

  override fun processResults(consumer: Processor<in T>): Boolean {
    return queries.all {
      it.forEach(consumer)
    }
  }

  override fun decompose(): FlatRequests<T> {
    val requests: List<QueryRequest<T, T>> = queries.map { QueryRequest(it, idTransform()) }
    return FlatRequests(queryRequests = requests)
  }
}
