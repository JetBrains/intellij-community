// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public interface SearchWordQuery extends Query<TextOccurrence> {

  @NotNull
  SearchWordParameters getParameters();
}
