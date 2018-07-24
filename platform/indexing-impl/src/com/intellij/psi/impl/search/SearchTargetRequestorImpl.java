// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.model.search.DefaultSymbolReferenceSearchParameters;
import com.intellij.model.search.SearchTargetRequestor;
import com.intellij.model.search.SymbolReferenceSearch;
import com.intellij.model.search.SymbolReferenceSearchParameters;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Preprocessor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.search.PsiSearchScopeUtil.restrictScopeTo;

final class SearchTargetRequestorImpl implements SearchTargetRequestor {

  private final @NotNull SearchRequestCollectorImpl myCollector;
  private final @NotNull Symbol myTarget;

  private SearchScope mySearchScope;
  private FileType[] myFileTypes;

  SearchTargetRequestorImpl(@NotNull SearchRequestCollectorImpl collector, @NotNull Symbol target) {
    myCollector = collector;
    myTarget = target;
  }

  @NotNull
  @Override
  public SearchTargetRequestor inScope(@NotNull SearchScope scope) {
    mySearchScope = scope;
    return this;
  }

  @NotNull
  @Override
  public SearchTargetRequestor restrictSearchScopeTo(@NotNull FileType... fileTypes) {
    myFileTypes = fileTypes;
    return this;
  }

  @Override
  public void search() {
    search(Preprocessor.id());
  }

  @Override
  public void search(@NotNull Preprocessor<SymbolReference, SymbolReference> preprocessor) {
    myCollector.searchSubQuery(SymbolReferenceSearch.search(createParameters()), preprocessor);
  }

  @NotNull
  private SearchScope getSearchScope() {
    SearchScope baseScope = mySearchScope == null ? myCollector.getParameters().getOriginalSearchScope() : mySearchScope;
    if (myFileTypes != null && myFileTypes.length > 0) {
      return restrictScopeTo(baseScope, myFileTypes);
    }
    else {
      return baseScope;
    }
  }

  private SymbolReferenceSearchParameters createParameters() {
    SymbolReferenceSearchParameters parameters = myCollector.getParameters();
    SearchScope searchScope = getSearchScope();
    return new DefaultSymbolReferenceSearchParameters(
      parameters.getProject(),
      myTarget,
      searchScope,
      parameters.isIgnoreUseScope()
    );
  }
}
