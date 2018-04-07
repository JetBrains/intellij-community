// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.ModelElement;
import org.jetbrains.annotations.NotNull;

public interface SearchWordRequestor {

  @NotNull
  SearchWordRequestor setCaseSensitive(boolean caseSensitive);

  @NotNull
  SearchWordRequestor setSearchContext(short searchContext);

  @NotNull
  SearchWordRequestor setTargetHint(@NotNull ModelElement target);

  void searchRequests(@NotNull OccurenceSearchRequestor occurenceSearchRequestor);

  void search(@NotNull ModelElement target);
}
