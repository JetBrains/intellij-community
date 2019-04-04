// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.SymbolReference
import com.intellij.model.search.SearchSymbolReferenceParameters
import com.intellij.model.search.SymbolSearchHelper
import com.intellij.psi.impl.search.idTransform
import com.intellij.util.Processor
import com.intellij.util.Query

internal class SymbolReferenceQueryImpl(
  private val myParameters: SearchSymbolReferenceParameters
) : AbstractDecomposableQuery<SymbolReference>() {

  private val myBaseQuery: Query<out SymbolReference> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    SymbolReferenceSearch.createQuery(myParameters)
  }

  override fun processResults(consumer: Processor<in SymbolReference>): Boolean {
    return myBaseQuery.forEach(consumer) && SymbolSearchHelper.getInstance(myParameters.project).runSearch(myParameters, consumer)
  }

  override fun decompose(): FlatRequests<SymbolReference> {
    val queryRequest: QueryRequest<*, SymbolReference> = QueryRequest(myBaseQuery, idTransform())
    val parametersRequest: ParamsRequest<SymbolReference> = ParamsRequest(myParameters, idTransform())
    return FlatRequests(myQueryRequests = listOf(queryRequest), myParamsRequests = listOf(parametersRequest))
  }
}
