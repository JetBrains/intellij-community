// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
  This is type of the key for data that must be visible to all threads
 */
public final class GlobalContextKey<T> extends Key<T> {
  public GlobalContextKey(@NotNull @NonNls String name) {
    super(name);
  }

  public synchronized T getOrCreate(@Nullable UserDataHolder holder, Supplier<? extends T> factory) {
    T result = get(holder);
    if (result == null) {
      set(holder, result = factory.get());
    }
    return result;
  }

  public static <T> GlobalContextKey<T> create(@NotNull @NonNls String name) {
    return new GlobalContextKey<>(name);
  }

}
