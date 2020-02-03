// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.search.*
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.util.EmptyQuery
import com.intellij.util.Query

class SearchServiceImpl : SearchService {

  override fun <T> searchParameters(parameters: SearchParameters<T>): Query<T> = SearchParametersQuery(parameters)

  override fun searchWord(project: Project, word: String): SearchWordQueryBuilder = SearchWordQueryBuilderImpl(project, word)

  override fun searchPsiSymbolReferences(project: Project, symbol: Symbol, searchScope: SearchScope): Query<PsiSymbolReference> {
    return searchParameters(DefaultPsiSymbolReferenceSearchParameters(project, symbol.createPointer(), searchScope))
  }

  override fun searchPsiSymbolDeclarations(project: Project, symbol: Symbol, searchScope: SearchScope): Query<PsiSymbolDeclaration> {
    return searchParameters(DefaultPsiSymbolDeclarationSearchParameters(project, symbol.createPointer(), searchScope))
  }

  private open class DefaultSymbolSearchParameters(
    private val project: Project,
    private val pointer: Pointer<out Symbol>,
    private val searchScope: SearchScope
  ) {
    fun areValid(): Boolean = pointer.dereference() != null
    fun getProject(): Project = project
    fun getSymbol(): Symbol = requireNotNull(pointer.dereference()) { "#getSymbol() must not be called on invalid parameters" }
    fun getSearchScope(): SearchScope = searchScope
  }

  private class DefaultPsiSymbolReferenceSearchParameters(
    project: Project,
    pointer: Pointer<out Symbol>,
    searchScope: SearchScope
  ) : DefaultSymbolSearchParameters(project, pointer, searchScope),
      PsiSymbolReferenceSearchParameters

  private class DefaultPsiSymbolDeclarationSearchParameters(
    project: Project,
    pointer: Pointer<out Symbol>,
    searchScope: SearchScope
  ) : DefaultSymbolSearchParameters(project, pointer, searchScope),
      PsiSymbolDeclarationSearchParameters

  override fun <T : Any?> merge(queries: List<Query<out T>>): Query<out T> {
    return when (queries.size) {
      0 -> EmptyQuery.getEmptyQuery()
      1 -> queries[0]
      else -> CompositeQuery(queries)
    }
  }
}
