// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.util.ClearableLazyValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class CachedSingletonsRegistry {
  @SuppressWarnings("InstantiationOfUtilityClass")
  private static final Object LOCK = new CachedSingletonsRegistry();

  private static final List<Class<?>> ourRegisteredClasses = new ArrayList<>();
  private static final List<ClearableLazyValue<?>> ourRegisteredLazyValues = new ArrayList<>();

  private CachedSingletonsRegistry() {}

  public static @Nullable <T> T markCachedField(@NotNull Class<T> klass) {
    synchronized (LOCK) {
      ourRegisteredClasses.add(klass);
    }
    return null;
  }

  public static @NotNull <T> ClearableLazyValue<T> markLazyValue(@NotNull ClearableLazyValue<T> lazyValue) {
    synchronized (LOCK) {
      ourRegisteredLazyValues.add(lazyValue);
    }
    return lazyValue;
  }

  public static void cleanupCachedFields() {
    synchronized (LOCK) {
      for (Class<?> aClass : ourRegisteredClasses) {
        try {
          Field field = aClass.getField("ourInstance");
          field.setAccessible(true);
          field.set(null, null);
        }
        catch (Exception e) {
          // Ignore cleanup failed.
          // In some cases, we cannot find ourInstance field if idea.jar is scrambled and the names of the private fields changed
        }
      }
      for (ClearableLazyValue<?> value : ourRegisteredLazyValues) {
        value.drop();
      }
    }
  }
}
