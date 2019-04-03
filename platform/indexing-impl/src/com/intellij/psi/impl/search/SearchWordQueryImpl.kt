// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.search.SearchWordParameters
import com.intellij.model.search.SearchWordQuery
import com.intellij.model.search.TextOccurrence
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor

class SearchWordQueryImpl(private val parameters: SearchWordParameters) : AbstractQuery<TextOccurrence>(), SearchWordQuery {

  override fun getParameters(): SearchWordParameters = parameters

  override fun processResults(consumer: Processor<in TextOccurrence>): Boolean {
    TODO()
  }
}
