// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Predicate;

public class QueryDefaults {

  private QueryDefaults() {}

  @NotNull
  static <I, O> Query<O> map(@NotNull Query<? extends I> base, @NotNull Function<? super I, ? extends O> transformation) {
    return new CustomProcessorQuery<>(base, PostProcessor.mapping(transformation));
  }

  @NotNull
  static <R> Query<R> filter(@NotNull Query<R> base, @NotNull Predicate<? super R> predicate) {
    return new CustomProcessorQuery<>(base, PostProcessor.filtering(predicate));
  }

  @NotNull
  static <B, R> Query<R> flatMap(@NotNull Query<? extends B> base,
                                 @NotNull Function<? super B, ? extends Query<? extends R>> mapper) {
    return new CustomProcessorQuery<>(
      base,
      (PostProcessor<R, B>)
        (Processor<? super R> resultProcessor) ->
          (B baseElement) -> {
            Query<? extends R> subquery = mapper.apply(baseElement);
            return subquery.forEach(resultProcessor);
          }
    );
  }
}
