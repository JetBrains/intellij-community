// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * PostProcessor that doesn't process anything, i.e. returns original processor.
 */
final class IdPostProcessor<R, B extends R> implements PostProcessor<R, B> {

  private static final PostProcessor<?, ?> INSTANCE = new IdPostProcessor();

  @Contract(pure = true)
  @NotNull
  static <R, B extends R> PostProcessor<R, B> getInstance() {
    //noinspection unchecked
    return (PostProcessor<R, B>)INSTANCE;
  }

  @NotNull
  @Override
  public Processor<? super B> apply(@NotNull Processor<? super R> processor) {
    return processor;
  }

  @Override
  public String toString() {
    return "ID";
  }
}
