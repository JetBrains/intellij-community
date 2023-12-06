// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.application;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public abstract class ReadActionProcessor<T> implements Processor<T> {
  @Override
  public boolean process(final T t) {
    return ReadAction.compute(() -> processInReadAction(t));
  }
  public abstract boolean processInReadAction(T t);

  public static @NotNull <T> Processor<T> wrapInReadAction(final @NotNull Processor<? super T> processor) {
    return t -> ReadAction.compute(() -> processor.process(t));
  }
}
