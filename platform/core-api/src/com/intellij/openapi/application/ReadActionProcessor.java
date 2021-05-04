// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.application;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public abstract class ReadActionProcessor<T> implements Processor<T> {
  @Override
  public boolean process(final T t) {
    return ReadAction.compute(() -> processInReadAction(t));
  }
  public abstract boolean processInReadAction(T t);

  @NotNull
  public static <T> Processor<T> wrapInReadAction(@NotNull final Processor<? super T> processor) {
    return t -> ReadAction.compute(() -> processor.process(t));
  }
}
