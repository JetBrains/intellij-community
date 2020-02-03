// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

public interface PsiSymbolDeclarationSearchParameters extends SearchParameters<PsiSymbolDeclaration> {

  /**
   * @return target symbol to search for declarations
   */
  @NotNull
  Symbol getSymbol();

  @NotNull
  SearchScope getSearchScope();
}
