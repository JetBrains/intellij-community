// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class LazyUtil {
  public static <T> NotNullLazyValue<T> create(@NotNull Supplier<T> supplier) {
    return new NotNullLazyValue<T>() {
      @NotNull
      @Override
      protected T compute() {
        return supplier.get();
      }
    };
  }
}
