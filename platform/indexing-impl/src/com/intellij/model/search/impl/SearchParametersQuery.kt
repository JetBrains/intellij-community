// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.search.SearchParameters
import com.intellij.psi.impl.search.runSearch
import com.intellij.util.Processor

internal class SearchParametersQuery<R>(
  private val myParameters: SearchParameters<out R>
) : AbstractDecomposableQuery<R>() {

  override fun processResults(consumer: Processor<in R>): Boolean {
    return runSearch(myParameters.project, this, consumer)
  }

  override fun decompose(): PrimitiveRequests<R> {
    val parametersRequest: ParametersRequest<R, R> = ParametersRequest(myParameters, idTransform())
    return PrimitiveRequests(Requests(parametersRequests = listOf(parametersRequest)))
  }
}
