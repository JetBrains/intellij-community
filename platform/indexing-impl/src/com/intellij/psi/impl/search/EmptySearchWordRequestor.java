// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.ModelElement;
import com.intellij.model.search.OccurenceSearchRequestor;
import com.intellij.model.search.SearchWordRequestor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

final class EmptySearchWordRequestor implements SearchWordRequestor {

  static final SearchWordRequestor INSTANCE = new EmptySearchWordRequestor();

  @NotNull
  @Override
  public SearchWordRequestor setSearchScope(@NotNull SearchScope searchScope) {
    return this;
  }

  @NotNull
  @Override
  public SearchWordRequestor restrictSearchScopeTo(@NotNull FileType... fileTypes) {
    return this;
  }

  @NotNull
  @Override
  public SearchWordRequestor setCaseSensitive(boolean caseSensitive) {
    return this;
  }

  @NotNull
  @Override
  public SearchWordRequestor setSearchContext(short searchContext) {
    return this;
  }

  @NotNull
  @Override
  public SearchWordRequestor setTargetHint(@NotNull ModelElement target) {
    return this;
  }

  @Override
  public void searchRequests(@NotNull OccurenceSearchRequestor occurenceSearchRequestor) {}

  @Override
  public void search(@NotNull ModelElement target) {}
}
