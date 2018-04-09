// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

public final class CustomProcessorQuery<B, R> extends AbstractQuery<R> {

  private final Query<B> myBaseQuery;
  private final Preprocessor<B, R> myPreprocessor;

  public CustomProcessorQuery(@NotNull Query<B> query, @NotNull Preprocessor<B, R> provider) {
    myBaseQuery = query;
    myPreprocessor = provider;
  }

  @Override
  protected boolean processResults(@NotNull Processor<R> consumer) {
    return myBaseQuery.forEach((Processor<B>)myPreprocessor.apply(consumer)::process);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CustomProcessorQuery<?, ?> query = (CustomProcessorQuery<?, ?>)o;

    if (!myBaseQuery.equals(query.myBaseQuery)) return false;
    if (!myPreprocessor.equals(query.myPreprocessor)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myBaseQuery.hashCode();
    result = 31 * result + myPreprocessor.hashCode();
    return result;
  }
}
