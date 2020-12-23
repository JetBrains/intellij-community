// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
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
   * Creates new query for searching PSI references by symbol.
   */
  @NotNull Query<@NotNull PsiSymbolReference> searchPsiSymbolReferences(@NotNull Project project,
                                                                        @NotNull Symbol symbol,
                                                                        @NotNull SearchScope searchScope);

  /**
   * Creates new query for searching PSI declarations by symbol.
   */
  @NotNull Query<@NotNull PsiSymbolDeclaration> searchPsiSymbolDeclarations(@NotNull Project project,
                                                                            @NotNull Symbol symbol,
                                                                            @NotNull SearchScope searchScope);

  /**
   * Merges a list of queries into a single query.
   */
  <T> @NotNull Query<? extends T> merge(@NotNull List<? extends @NotNull Query<? extends T>> queries);
}
