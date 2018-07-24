// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.openapi.application.DumbAwareSearchParameters;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

public interface SymbolReferenceSearchParameters extends DumbAwareSearchParameters {

  @Override
  default boolean isQueryValid() {
    return getTarget().isValid();
  }

  @NotNull
  Symbol getTarget();

  @NotNull
  SearchScope getOriginalSearchScope();

  boolean isIgnoreUseScope();

  @NotNull
  SearchScope getEffectiveSearchScope();
}
