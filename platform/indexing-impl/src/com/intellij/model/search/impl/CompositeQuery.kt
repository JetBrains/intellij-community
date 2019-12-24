// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.search.impl.PrimitiveRequests.Companion.plus
import com.intellij.util.Processor
import com.intellij.util.Query

internal class CompositeQuery<R>(
  private val queries: Collection<Query<out R>>
) : AbstractDecomposableQuery<R>() {

  override fun processResults(consumer: Processor<in R>): Boolean {
    return queries.all {
      it.forEach(consumer)
    }
  }

  override fun decompose(): PrimitiveRequests<R> {
    var result: PrimitiveRequests<R> = PrimitiveRequests.empty()
    for (query in queries) {
      result += decompose(query)
    }
    return result
  }
}
