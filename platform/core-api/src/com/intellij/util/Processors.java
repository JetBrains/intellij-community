// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class Processors {
  @NotNull
  public static <T> Processor<T> filter(@NotNull Processor<? super T> processor,
                                        @NotNull Condition<? super T> filter) {
    return o -> !filter.value(o) || processor.process(o);
  }

  @NotNull
  public static <T, S> Processor<S> map(@NotNull Processor<T> processor,
                                        @NotNull Function<? super S, ? extends T> map) {
    return o -> processor.process(map.fun(o));
  }

  @NotNull
  public static <T> Processor<T> cancelableCollectProcessor(@NotNull Collection<T> collection) {
    return new CommonProcessors.CollectProcessor<T>(collection){
      @Override
      public boolean process(T t) {
        ProgressManager.checkCanceled();
        return super.process(t);
      }
    };
  }
}
