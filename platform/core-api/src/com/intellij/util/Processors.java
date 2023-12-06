// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Predicate;

public final class Processors {
  public static @NotNull <T> Processor<T> filter(@NotNull Processor<? super T> processor,
                                                 @NotNull Predicate<? super T> filter) {
    return o -> !filter.test(o) || processor.process(o);
  }

  public static @NotNull <T, S> Processor<S> map(@NotNull Processor<? super T> processor,
                                                 @NotNull Function<? super S, ? extends T> map) {
    return o -> processor.process(map.fun(o));
  }

  public static @NotNull <T> Processor<T> cancelableCollectProcessor(@NotNull Collection<T> collection) {
    return new CommonProcessors.CollectProcessor<T>(collection) {
      @Override
      public boolean process(T t) {
        ProgressManager.checkCanceled();
        return super.process(t);
      }
    };
  }
}
