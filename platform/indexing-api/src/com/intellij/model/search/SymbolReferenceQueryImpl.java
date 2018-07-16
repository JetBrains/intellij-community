// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.SymbolReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.AbstractQuery;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

final class SymbolReferenceQueryImpl extends AbstractQuery<SymbolReference> implements SymbolReferenceQuery {

  private final SymbolReferenceSearchParameters myParameters;

  SymbolReferenceQueryImpl(@NotNull SymbolReferenceSearchParameters parameters) {
    myParameters = parameters;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super SymbolReference> consumer) {
    return getBaseQuery().forEach(consumer) &&
           SymbolSearchHelper.getInstance(myParameters.getProject()).runSearch(myParameters, consumer);
  }

  @Contract(pure = true)
  @NotNull
  public SymbolReferenceSearchParameters getParameters() {
    return myParameters;
  }

  @Contract(pure = true)
  @NotNull
  public Query<SymbolReference> getBaseQuery() {
    return SymbolReferenceSearch.INSTANCE.createQuery(myParameters);
  }

  @NotNull
  @Override
  public SymbolReferenceQuery inScope(@NotNull SearchScope scope) {
    if (myParameters.getOriginalSearchScope().equals(scope)) {
      return this;
    }
    return new SymbolReferenceQueryImpl(new DefaultSymbolReferenceSearchParameters(
      myParameters.getProject(),
      myParameters.getTarget(),
      scope,
      myParameters.isIgnoreAccessScope()
    ));
  }

  @NotNull
  @Override
  public SymbolReferenceQuery ignoreAccessScope() {
    if (myParameters.isIgnoreAccessScope()) {
      return this;
    }
    return new SymbolReferenceQueryImpl(new DefaultSymbolReferenceSearchParameters(
      myParameters.getProject(),
      myParameters.getTarget(),
      myParameters.getOriginalSearchScope(),
      true
    ));
  }
}
