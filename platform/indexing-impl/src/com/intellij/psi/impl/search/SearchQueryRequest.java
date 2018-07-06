// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.SymbolReference;
import com.intellij.util.Preprocessor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

final class SearchQueryRequest<T> {

  final @NotNull Query<T> query;
  final @NotNull Preprocessor<SymbolReference, T> preprocessor;

  SearchQueryRequest(@NotNull Query<T> query, @NotNull Preprocessor<SymbolReference, T> preprocessor) {
    this.query = query;
    this.preprocessor = preprocessor;
  }

  @Override
  public String toString() {
    return "SearchQueryRequest{" +
           "query=" + query +
           ", preprocessor=" + preprocessor +
           '}';
  }
}
