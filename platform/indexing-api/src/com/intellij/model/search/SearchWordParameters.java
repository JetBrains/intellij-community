// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
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

  interface Builder {
    /**
     * Sets search scope.<br/>
     */
    @Contract("_ -> new")
    @NotNull
    Builder inScope(@NotNull SearchScope searchScope);

    /**
     * Restricts search scope to include only selected file types.
     */
    @Contract("_ -> new")
    @NotNull
    Builder restrictScopeTo(@NotNull FileType... fileTypes);

    @Contract("-> new")
    @NotNull
    Builder caseInsensitive();

    @Contract("_, _ -> new")
    @NotNull
    Builder inContexts(@NotNull SearchContext context, @NotNull SearchContext... otherContexts);

    @Contract("-> new")
    @NotNull
    Builder inAllContexts();

    /**
     * Sets target hint which is used for optimizing search requests.
     * Target is automatically set when using {@link #build(Symbol)}.
     */
    @Contract("_ -> new")
    @NotNull
    Builder withTargetHint(@NotNull Symbol target);

    @Contract("-> new")
    @NotNull
    Query<? extends TextOccurrence> build();

    @Contract("_ -> new")
    @NotNull
    Query<? extends SymbolReference> build(@NotNull Symbol target);
  }
}
