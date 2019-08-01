// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

public interface SymbolReferenceSearchParameters extends SearchParameters<SymbolReference> {

  /**
   * @return target symbol to search for references
   */
  @NotNull
  Symbol getSymbol();

  @NotNull
  SearchScope getSearchScope();
}
