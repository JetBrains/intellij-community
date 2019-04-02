// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface SearchWordParameters {

  @NotNull
  Project getProject();

  @NotNull
  String getWord();

  @NotNull
  SearchScope getSearchScope();

  boolean isCaseSensitive();

  @Nullable
  Symbol getTargetHint();

  @NotNull
  Set<SearchContext> getSearchContexts();

  /**
   * Sets search scope.<br/>
   * If search scope is left unset then {@link SearchRequestCollector#getParameters() original} scope will be used.
   */
  @Contract("_ -> this")
  @NotNull
  SearchWordParameters inScope(@NotNull SearchScope searchScope);

  /**
   * Restricts search scope to include only selected file types.
   */
  @Contract("_ -> this")
  @NotNull
  SearchWordParameters restrictScopeTo(@NotNull FileType... fileTypes);

  @Contract("-> this")
  @NotNull
  SearchWordParameters caseInsensitive();

  @Contract("_, _ -> this")
  @NotNull
  SearchWordParameters inContexts(@NotNull SearchContext context, @NotNull SearchContext... otherContexts);

  @Contract("-> this")
  @NotNull
  SearchWordParameters inAllContexts();

  /**
   * Sets target hint which is used for optimizing search requests.
   * Target is automatically set when using {@link #search(Symbol)}.
   */
  @Contract("_ -> this")
  @NotNull
  SearchWordParameters withTargetHint(@NotNull Symbol target);
}
