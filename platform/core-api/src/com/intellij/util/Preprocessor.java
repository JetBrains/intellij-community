// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * {@code Processor<Result> -> Processor<Base>}
 */
public interface Preprocessor<Base, Result> extends Function<Processor<? super Result>, Processor<? super Base>> {

  /**
   * @return {@link Base base} processor that processes and feeds elements into {@link Result result} processor
   */
  @NotNull
  @Override
  Processor<? super Base> apply(@NotNull Processor<? super Result> processor);


  /**
   * @return preprocessor returning original processor,
   * meaning elements from base processor will be passed into result processor as is.
   * Note that no casts are required because {@link Base} is a subtype of {@link Result}
   */
  @NotNull
  static <Base extends Result, Result> Preprocessor<Base, Result> id() {
    return p -> p;
  }

  /**
   * {@code (Processor<Result> -> Processor<Base>) -> (Processor<V> -> Processor<Result>) -> (Processor<V> -> Processor<Base>)}
   */
  @NotNull
  static <V, Base, Result> Preprocessor<Base, V> compose(@NotNull Preprocessor<? super Base, ? extends Result> after,
                                                         @NotNull Preprocessor<? super Result, ? extends V> before) {
    return v -> after.apply(before.apply(v));
  }
}
