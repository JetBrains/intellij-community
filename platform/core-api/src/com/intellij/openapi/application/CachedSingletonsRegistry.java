// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CachedSingletonsRegistry {
  @SuppressWarnings("InstantiationOfUtilityClass")
  private static final Object LOCK = new CachedSingletonsRegistry();

  private static final List<Class<?>> ourRegisteredClasses = new ArrayList<>();
  private static final List<ClearableLazyValue<?>> ourRegisteredLazyValues = new ArrayList<>();

  private CachedSingletonsRegistry() {}

  @Nullable
  public static <T> T markCachedField(@NotNull Class<T> klass) {
    synchronized (LOCK) {
      ourRegisteredClasses.add(klass);
    }
    return null;
  }

  @NotNull
  public static <T> ClearableLazyValue<T> markLazyValue(@NotNull ClearableLazyValue<T> lazyValue) {
    synchronized (LOCK) {
      ourRegisteredLazyValues.add(lazyValue);
    }
    return lazyValue;
  }

  public static void cleanupCachedFields() {
    synchronized (LOCK) {
      for (Class<?> aClass : ourRegisteredClasses) {
        try {
          cleanupClass(aClass);
        }
        catch (Exception e) {
          // Ignore cleanup failed. In some cases we cannot find ourInstance field if idea.jar is scrambled and names of the private fields changed
        }
      }
      for (ClearableLazyValue<?> value : ourRegisteredLazyValues) {
        value.drop();
      }
    }
  }

  private static void cleanupClass(Class<?> aClass) {
    ReflectionUtil.resetField(aClass, null, "ourInstance");
  }
}
