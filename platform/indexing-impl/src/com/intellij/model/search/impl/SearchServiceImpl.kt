// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.Symbol
import com.intellij.model.SymbolReference
import com.intellij.model.search.SearchService
import com.intellij.model.search.SearchSymbolReferenceParameters
import com.intellij.model.search.SearchWordParameters
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.search.filtering
import com.intellij.psi.impl.search.mapping
import com.intellij.util.Query
import java.util.function.Function
import java.util.function.Predicate

class SearchServiceImpl : SearchService {

  override fun searchTarget(project: Project, symbol: Symbol): SearchSymbolReferenceParameters.Builder {
    return SearchSymbolReferenceParametersImpl(project, symbol)
  }

  override fun searchTarget(parameters: SearchSymbolReferenceParameters): Query<out SymbolReference> {
    return SymbolReferenceQueryImpl(parameters)
  }

  override fun searchWord(project: Project, word: String): SearchWordParameters.Builder {
    return SearchWordParametersImpl(project, word)
  }

  override fun <B, R> map(base: Query<out B>, transformation: Function<in B, out R>): Query<out R> {
    return TransformingQuery(base, mapping(transformation))
  }

  override fun <R> filter(base: Query<out R>, predicate: Predicate<in R>): Query<out R> {
    return TransformingQuery(base, filtering(predicate))
  }

  override fun <B, R> mapSubquery(base: Query<out B>, subquery: Function<in B, out Query<out R>>): Query<out R> {
    return mapSubqueries(base, mapping(subquery))
  }

  override fun <B, R> mapSubqueries(base: Query<out B>, subqueries: Subqueries<B, R>): Query<out R> {
    return LayeredQuery(base, subqueries)
  }
}
