// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search.impl

import com.intellij.model.search.SearchParameters
import com.intellij.model.search.SearchService
import com.intellij.model.search.SearchWordQueryBuilder
import com.intellij.openapi.project.Project
import com.intellij.util.EmptyQuery
import com.intellij.util.Query

internal class SearchServiceImpl : SearchService {

  override fun <T> searchParameters(parameters: SearchParameters<T>): Query<out T> = SearchParametersQuery(parameters)

  override fun searchWord(project: Project, word: String): SearchWordQueryBuilder = SearchWordQueryBuilderImpl(project, word)

  override fun <T> merge(queries: List<Query<out T>>): Query<out T> {
    return when (queries.size) {
      0 -> EmptyQuery.getEmptyQuery()
      1 -> queries[0]
      else -> CompositeQuery(queries)
    }
  }
}
