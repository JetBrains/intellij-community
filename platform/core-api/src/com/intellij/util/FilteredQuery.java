// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class FilteredQuery<T> extends CustomProcessorQuery<T, T> {

  public FilteredQuery(@NotNull Query<T> original, @NotNull Condition<T> filter) {
    super(original, Preprocessor.filtering(filter::value));
  }

  @Nullable
  @Override
  public T findFirst() {
    return super.findFirst();
  }
}
