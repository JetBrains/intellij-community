// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.Symbol
import com.intellij.model.search.*
import com.intellij.model.search.impl.SymbolReferenceQueryImpl
import com.intellij.openapi.project.Project
import com.intellij.util.Query
import java.util.function.Function
import java.util.function.Predicate

class SearchServiceImpl : SearchService {

  override fun searchTarget(project: Project, symbol: Symbol): SearchSymbolReferenceParameters.Builder {
    return SearchSymbolReferenceParametersImpl(project, symbol)
  }

  override fun searchTarget(parameters: SearchSymbolReferenceParameters): SymbolReferenceQuery {
    return SymbolReferenceQueryImpl(parameters)
  }

  override fun searchWord(project: Project, word: String): SearchWordParameters.Builder {
    return SearchWordParametersImpl(project, word)
  }

  override fun <B, R> map(base: Query<out B>, transformation: Function<in B, out R>): Query<out R> {
    return TransformingQuery.mapping(base, transformation)
  }

  override fun <R> filter(base: Query<out R>, predicate: Predicate<in R>): Query<out R> {
    return TransformingQuery.filtering(base, predicate)
  }
}
