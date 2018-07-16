// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.SymbolReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public interface SymbolReferenceQuery extends Query<SymbolReference> {

  @NotNull
  SymbolReferenceSearchParameters getParameters();

  @NotNull
  Query<SymbolReference> getBaseQuery();

  /**
   * @return new query instance with adjusted search scope or this instance if passed search scope is equal to original
   */
  @NotNull
  SymbolReferenceQuery inScope(@NotNull SearchScope scope);

  /**
   * @return new query instance which will ignore access scope or this instance if access scope is already ignored
   */
  @NotNull
  SymbolReferenceQuery ignoreAccessScope();
}
