// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public interface SearchService {

  @NotNull
  static SearchService getInstance() {
    return ServiceManager.getService(SearchService.class);
  }

  @NotNull
  SearchSymbolReferenceParameters.Builder searchTarget(@NotNull Project project, @NotNull Symbol symbol);

  @NotNull
  Query<? extends SymbolReference> searchTarget(@NotNull SearchSymbolReferenceParameters parameters);

  @NotNull
  SearchWordParameters.Builder searchWord(@NotNull Project project, @NotNull String word);
}
