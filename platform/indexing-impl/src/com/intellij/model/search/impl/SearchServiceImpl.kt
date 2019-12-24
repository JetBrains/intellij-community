// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.SymbolReference
import com.intellij.model.search.SearchParameters
import com.intellij.model.search.SearchService
import com.intellij.model.search.SearchWordQueryBuilder
import com.intellij.model.search.SymbolReferenceSearchParameters
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.util.EmptyQuery
import com.intellij.util.Query

class SearchServiceImpl : SearchService {

  override fun <T> searchParameters(parameters: SearchParameters<T>): Query<T> = SearchParametersQuery(parameters)

  override fun searchWord(project: Project, word: String): SearchWordQueryBuilder = SearchWordQueryBuilderImpl(project, word)

  override fun searchSymbol(project: Project, symbol: Symbol, searchScope: SearchScope): Query<SymbolReference> {
    return searchParameters(DefaultSymbolReferenceSearchParameters(project, symbol.createPointer(), searchScope))
  }

  private class DefaultSymbolReferenceSearchParameters(
    private val project: Project,
    private val pointer: Pointer<out Symbol>,
    private val searchScope: SearchScope
  ) : SymbolReferenceSearchParameters {
    override fun areValid(): Boolean = pointer.dereference() != null
    override fun getProject(): Project = project
    override fun getSymbol(): Symbol = requireNotNull(pointer.dereference()) { "#getSymbol() must not be called on invalid parameters" }
    override fun getSearchScope(): SearchScope = searchScope
  }

  override fun <T : Any?> merge(queries: List<Query<out T>>): Query<out T> {
    return when (queries.size) {
      0 -> EmptyQuery.getEmptyQuery()
      1 -> queries[0]
      else -> CompositeQuery(queries)
    }
  }
}
