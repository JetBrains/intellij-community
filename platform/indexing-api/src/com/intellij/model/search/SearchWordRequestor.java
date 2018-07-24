// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface SearchWordRequestor {

  /**
   * Sets search scope.<br/>
   * If search scope is left unset then {@link SearchRequestCollector#getParameters() original} scope will be used.
   */
  @Contract("_ -> this")
  @NotNull
  SearchWordRequestor inScope(@NotNull SearchScope searchScope);

  /**
   * Restricts search scope to include only selected file types.
   */
  @Contract("_ -> this")
  @NotNull
  SearchWordRequestor restrictSearchScopeTo(@NotNull FileType... fileTypes);

  @Contract("-> this")
  @NotNull
  SearchWordRequestor caseInsensitive();

  @Contract("_, _ -> this")
  @NotNull
  SearchWordRequestor inContexts(@NotNull SearchContext context, @NotNull SearchContext... otherContexts);

  @Contract("-> this")
  @NotNull
  SearchWordRequestor inAllContexts();

  /**
   * Sets target hint which is used for optimizing search requests.
   * Target is automatically set when using {@link #search(Symbol)}.
   */
  @Contract("_ -> this")
  @NotNull
  SearchWordRequestor withTargetHint(@NotNull Symbol target);

  /**
   * Orders to search for word occurrences and process them with passed requestor. <br/>
   * {@link SearchRequestCollector} is passed into the requestor allowing to order more sub searches.
   */
  void searchRequests(@NotNull OccurrenceSearchRequestor occurrenceSearchRequestor);

  /**
   * Orders to search for references with word that resolve into target and pass them as is into result processor.
   *
   * @param target
   */
  void search(@NotNull Symbol target);
}
