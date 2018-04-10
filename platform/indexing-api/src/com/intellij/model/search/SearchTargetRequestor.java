// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.ModelReference;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Preprocessor;
import org.jetbrains.annotations.NotNull;

public interface SearchTargetRequestor {

  @NotNull
  SearchTargetRequestor setSearchScope(@NotNull SearchScope scope);

  @NotNull
  SearchTargetRequestor restrictSearchScopeTo(@NotNull FileType... fileTypes);

  void search();

  void search(@NotNull Preprocessor<ModelReference, ModelReference> preprocessor);
}
