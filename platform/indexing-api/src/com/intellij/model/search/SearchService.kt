// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Service for building search queries.
 */
public interface SearchService {

  static @NotNull SearchService getInstance() {
    return ApplicationManager.getApplication().getService(SearchService.class);
  }

  /**
   * Creates a query which doesn't perform any search on itw own,
   * and instead collects search requests from {@linkplain Searcher searchers}.
   */
  <T> @NotNull Query<T> searchParameters(@NotNull SearchParameters<T> parameters);

  /**
   * Creates new builder of text occurrences query.
   */
  @NotNull SearchWordQueryBuilder searchWord(@NotNull Project project, @NotNull String word);

  /**
   * Merges a list of queries into a single query.
   */
  <T> @NotNull Query<? extends T> merge(@NotNull List<? extends @NotNull Query<? extends T>> queries);
}
