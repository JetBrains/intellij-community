// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Preprocessor that doesn't pre-process anything, i.e. returns original processor.
 */
final class IdPreprocessor<R, B extends R> implements Preprocessor<R, B> {

  private static final Preprocessor<?, ?> INSTANCE = new IdPreprocessor();

  @Contract(pure = true)
  @NotNull
  static <R, B extends R> Preprocessor<R, B> getInstance() {
    //noinspection unchecked
    return (Preprocessor<R, B>)INSTANCE;
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
