// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.search.SearchParameters
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.impl.search.searchers
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor

internal class SearchersQuery<R : Any>(
  private val parameters: SearchParameters<R>
) : AbstractQuery<R>() {

  override fun processResults(consumer: Processor<in R>): Boolean {
    return runReadAction {
      processInReadAction(consumer)
    }
  }

  private fun processInReadAction(consumer: Processor<in R>): Boolean {
    val searchers = searchers(parameters)
    for (searcher in searchers) {
      val immediateResults = searcher.collectImmediateResults(parameters)
      for (result in immediateResults) {
        if (!consumer.process(result)) {
          return false
        }
      }
    }
    return true
  }
}
