// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.ModelReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public interface SearchRequestCollector {

  void searchSubQuery(@NotNull Query<? extends ModelReference> subQuery);

  @NotNull
  SearchWordRequestor searchWord(@NotNull String word, @NotNull SearchScope searchScope);
}
