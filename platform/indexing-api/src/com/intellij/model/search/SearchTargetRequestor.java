// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.SymbolReference;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Preprocessor;
import com.intellij.util.Query;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * This requestor is essentially {@link SearchSymbolReferenceParameters} builder.
 */
public interface SearchTargetRequestor {

  /**
   * Sets search scope.<br/>
   * If search scope is left unset then {@link SearchRequestCollector#getParameters() original} scope will be used.
   */
  @Contract("_ -> this")
  @NotNull
  SearchTargetRequestor inScope(@NotNull SearchScope scope);

  /**
   * Restricts search scope to include only selected file types.
   */
  @Contract("_ -> this")
  @NotNull
  SearchTargetRequestor restrictSearchScopeTo(@NotNull FileType... fileTypes);

  /**
   * Orders to search the target. <br/>
   * This method builds {@link SearchSymbolReferenceParameters search parameters}
   * and orders {@link SearchRequestCollector#searchSubQuery sub query search}.
   */
  default void search() {
    search(Preprocessor.id());
  }

  default void searchFiltering(@NotNull Predicate<? super SymbolReference> predicate) {
    search(Preprocessor.filtering(predicate));
  }

  default void searchMapping(@NotNull Function<? super SymbolReference, ? extends SymbolReference> map) {
    search(Preprocessor.mapping(map));
  }

  /**
   * Orders to search the target. <br/>
   * This method builds {@link SearchSymbolReferenceParameters search parameters}
   * and orders {@link SearchRequestCollector#searchSubQuery(Query, Preprocessor) sub query search} with preprocessor.
   */
  void search(@NotNull Preprocessor<SymbolReference, SymbolReference> preprocessor);
}
