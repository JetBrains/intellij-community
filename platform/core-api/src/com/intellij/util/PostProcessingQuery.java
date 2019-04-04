// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

public class PostProcessingQuery<B, R> extends AbstractQuery<R> {

  protected final Query<? extends B> myBaseQuery;
  private final PostProcessor<R, B> myPostProcessor;

  public PostProcessingQuery(@NotNull Query<? extends B> query, @NotNull PostProcessor<R, B> provider) {
    myBaseQuery = query;
    myPostProcessor = provider;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super R> consumer) {
    return myBaseQuery.forEach(myPostProcessor.apply(consumer));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PostProcessingQuery<?, ?> query = (PostProcessingQuery<?, ?>)o;

    if (!myBaseQuery.equals(query.myBaseQuery)) return false;
    if (!myPostProcessor.equals(query.myPostProcessor)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myBaseQuery.hashCode();
    result = 31 * result + myPostProcessor.hashCode();
    return result;
  }
}
