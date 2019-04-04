// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.openapi.application.DumbAwareSearchParameters;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public interface SearchSymbolReferenceParameters extends DumbAwareSearchParameters {

  @NotNull
  Symbol getTarget();

  @NotNull
  SearchScope getOriginalSearchScope();

  boolean isIgnoreUseScope();

  @NotNull
  SearchScope getEffectiveSearchScope();

  interface Builder {

    /**
     * @return new query instance with adjusted search scope or this instance if passed search scope is equal to original
     */
    @NotNull
    Builder inScope(@NotNull SearchScope scope);

    /**
     * @return new query instance which will ignore use scope or this instance if use scope is already ignored
     */
    @NotNull
    Builder ignoreUseScope();

    @NotNull
    Builder ignoreUseScope(boolean ignore);

    @NotNull
    Query<? extends SymbolReference> build();
  }
}
