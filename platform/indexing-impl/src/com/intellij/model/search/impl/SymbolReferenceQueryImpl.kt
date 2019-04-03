// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.SymbolReference
import com.intellij.model.search.SearchSymbolReferenceParameters
import com.intellij.model.search.SymbolReferenceQuery
import com.intellij.model.search.SymbolSearchHelper
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor
import com.intellij.util.Query
import org.jetbrains.annotations.Contract

class SymbolReferenceQueryImpl(
  private val myParameters: SearchSymbolReferenceParameters
) : AbstractQuery<SymbolReference>(), SymbolReferenceQuery {

  override fun processResults(consumer: Processor<in SymbolReference>): Boolean {
    return baseQuery.forEach(consumer) && SymbolSearchHelper.getInstance(myParameters.project).runSearch(myParameters, consumer)
  }

  @Contract(pure = true)
  override fun getParameters(): SearchSymbolReferenceParameters = myParameters

  @Contract(pure = true)
  override fun getBaseQuery(): Query<SymbolReference> = SymbolReferenceSearch.createQuery(myParameters)
}
