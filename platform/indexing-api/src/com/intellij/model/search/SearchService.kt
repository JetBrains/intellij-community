// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.Query
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * Service for building search queries.
 */
@NonExtendable
interface SearchService {

  /**
   * Creates a query which doesn't perform any search on itw own,
   * and instead collects search requests from [searchers][Searcher].
   */
  fun <T> searchParameters(parameters: SearchParameters<T>): Query<out T>

  /**
   * Creates new builder of text occurrences query.
   */
  fun searchWord(project: Project, word: String): SearchWordQueryBuilder

  /**
   * Merges a list of queries into a single query.
   */
  fun <T> merge(queries: List<@JvmWildcard Query<out T>>): Query<out T>

  companion object {

    @JvmStatic
    fun getInstance(): SearchService = ApplicationManager.getApplication().service()
  }
}
