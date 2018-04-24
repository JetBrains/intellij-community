// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.ModelReference;
import com.intellij.model.search.ModelReferenceSearchParameters;
import com.intellij.util.Preprocessor;
import org.jetbrains.annotations.NotNull;

final class SearchParamsRequest {

  final @NotNull ModelReferenceSearchParameters parameters;
  final @NotNull Preprocessor<ModelReference, ModelReference> preprocessor;

  SearchParamsRequest(@NotNull ModelReferenceSearchParameters parameters,
                      @NotNull Preprocessor<ModelReference, ModelReference> preprocessor) {
    this.parameters = parameters;
    this.preprocessor = preprocessor;
  }

  @Override
  public String toString() {
    return "SearchParamsRequest{" +
           "parameters=" + parameters +
           ", preprocessor=" + preprocessor +
           '}';
  }
}
