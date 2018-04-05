// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

public final class CustomProcessorQuery<B, R> extends AbstractQuery<R> {

  private final Query<B> myBaseQuery;
  private final ProcessorMapper<B, R> myProcessorMapper;

  public CustomProcessorQuery(@NotNull Query<B> query, @NotNull ProcessorMapper<B, R> provider) {
    myBaseQuery = query;
    myProcessorMapper = provider;
  }

  @Override
  protected boolean processResults(@NotNull Processor<R> consumer) {
    return myBaseQuery.forEach(myProcessorMapper.apply(consumer));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CustomProcessorQuery<?, ?> query = (CustomProcessorQuery<?, ?>)o;

    if (!myBaseQuery.equals(query.myBaseQuery)) return false;
    if (!myProcessorMapper.equals(query.myProcessorMapper)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myBaseQuery.hashCode();
    result = 31 * result + myProcessorMapper.hashCode();
    return result;
  }
}
