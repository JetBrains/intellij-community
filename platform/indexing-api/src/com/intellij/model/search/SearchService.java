// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Service for building search queries.
 */
public interface SearchService {

  @NotNull
  static SearchService getInstance() {
    return ServiceManager.getService(SearchService.class);
  }

  /**
   * Creates a query which doesn't perform any search on itw own,
   * and instead collects search requests from {@linkplain Searcher searchers}.
   */
  @NotNull
  <T> Query<T> searchParameters(@NotNull SearchParameters<T> parameters);

  /**
   * Creates new builder of text occurrences query.
   */
  @NotNull
  SearchWordQueryBuilder searchWord(@NotNull Project project, @NotNull String word);

  /**
   * Creates new query for searching references by symbol.
   */
  @NotNull
  Query<SymbolReference> searchSymbolReferences(@NotNull Project project, @NotNull Symbol symbol, @NotNull SearchScope searchScope);

  /**
   * Merges a list of queries into a single query.
   */
  @NotNull
  <T> Query<? extends T> merge(@NotNull List<? extends Query<? extends T>> queries);
}
