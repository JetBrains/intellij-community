// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.SymbolReference;
import com.intellij.util.AbstractQuery;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class SymbolReferenceSearchQuery extends AbstractQuery<SymbolReference> {

  private final SymbolReferenceSearchParameters myParameters;
  private final Query<SymbolReference> myBaseQuery;

  public SymbolReferenceSearchQuery(@NotNull SymbolReferenceSearchParameters parameters, @NotNull Query<SymbolReference> baseQuery) {
    myParameters = parameters;
    myBaseQuery = baseQuery;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super SymbolReference> consumer) {
    return myBaseQuery.forEach(consumer) &&
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
    return myBaseQuery;
  }
}
