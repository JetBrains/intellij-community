// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class CachedSingletonsRegistry {
  @SuppressWarnings("InstantiationOfUtilityClass")
  private static final Object LOCK = new CachedSingletonsRegistry();

  private static final List<SynchronizedClearableLazy<?>> ourRegisteredLazyValues = new ArrayList<>();

  private CachedSingletonsRegistry() {}

  public static @NotNull <T> Supplier<T> lazy(@NotNull Supplier<? extends T> supplier) {
    SynchronizedClearableLazy<T> result = new SynchronizedClearableLazy<>(supplier::get);
    synchronized (LOCK) {
      ourRegisteredLazyValues.add(result);
    }
    return result;
  }

  /**
   * @deprecated Do not use.
   */
  @Deprecated
  public static @Nullable <T> T markCachedField(@SuppressWarnings("unused") @NotNull Class<T> klass) {
    return null;
  }

  public static void cleanupCachedFields() {
    synchronized (LOCK) {
      for (SynchronizedClearableLazy<?> value : ourRegisteredLazyValues) {
        value.drop();
      }
    }
  }
}
