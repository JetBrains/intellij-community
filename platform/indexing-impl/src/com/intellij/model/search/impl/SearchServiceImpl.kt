// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.search.SearchParameters
import com.intellij.model.search.SearchService
import com.intellij.model.search.SearchWordQueryBuilder
import com.intellij.openapi.project.Project
import com.intellij.util.EmptyQuery
import com.intellij.util.Query

class SearchServiceImpl : SearchService {

  override fun <T> searchParameters(parameters: SearchParameters<T>): Query<T> = SearchParametersQuery(parameters)

  override fun searchWord(project: Project, word: String): SearchWordQueryBuilder = SearchWordQueryBuilderImpl(project, word)

  override fun <T : Any?> merge(queries: List<Query<out T>>): Query<out T> {
    return when (queries.size) {
      0 -> EmptyQuery.getEmptyQuery()
      1 -> queries[0]
      else -> CompositeQuery(queries)
    }
  }
}
