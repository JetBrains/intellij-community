// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.search.SearchParameters
import com.intellij.model.search.SymbolSearchHelper
import com.intellij.psi.impl.search.idTransform
import com.intellij.util.Processor

internal class SearchParametersQuery<R>(private val myParameters: SearchParameters<R>) : AbstractDecomposableQuery<R>() {

  override fun processResults(consumer: Processor<in R>): Boolean {
    return SymbolSearchHelper.getInstance(myParameters.project).runSearch(myParameters, consumer)
  }

  override fun decompose(): FlatRequests<R> {
    val parametersRequest: ParametersRequest<R, R> = ParametersRequest(myParameters, idTransform())
    return FlatRequests(parametersRequests = listOf(parametersRequest))
  }
}
