// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ExtensibleQueryFactory;
import org.jetbrains.annotations.NotNull;

public final class SymbolReferenceSearch extends ExtensibleQueryFactory<SymbolReference, SymbolReferenceSearchParameters> {

  static final SymbolReferenceSearch INSTANCE = new SymbolReferenceSearch();

  private SymbolReferenceSearch() {}

  @NotNull
  public static SymbolReferenceQuery search(@NotNull Project project, @NotNull Symbol target) {
    return search(new DefaultSymbolReferenceSearchParameters(project, target, GlobalSearchScope.allScope(project), false));
  }

  @NotNull
  public static SymbolReferenceQuery search(@NotNull SymbolReferenceSearchParameters parameters) {
    return new SymbolReferenceQueryImpl(parameters);
  }
}
