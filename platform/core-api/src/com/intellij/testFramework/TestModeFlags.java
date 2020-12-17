// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provides a centralized place for changing the behavior of the IDE in tests so that specific
 * features can be tested.
 *
 * @author yole
 */
public final class TestModeFlags {
  private static final Map<String, Object> ourFlags = new HashMap<>();
  private static final List<TestModeFlagListener> ourListeners = new CopyOnWriteArrayList<>();

  @TestOnly
  public static <T> T set(@NotNull Key<T> flag, @Nullable T value) {
    //noinspection unchecked
    T oldValue = (T)ourFlags.get(flag.toString());
    ourFlags.put(flag.toString(), value);
    for (TestModeFlagListener listener : ourListeners) {
      listener.testModeFlagChanged(flag, value);
    }
    return oldValue;
  }

  @TestOnly
  public static void reset(@NotNull Key<?> flag) {
    set(flag, null);
  }

  @TestOnly
  public static <T> void set(@NotNull Key<T> flag, T value, @NotNull Disposable parentDisposable) {
    T oldValue = get(flag);
    set(flag, value);
    Disposer.register(parentDisposable, () -> set(flag, oldValue));
  }

  public static <T> T get(@NotNull Key<T> flag) {
    //noinspection unchecked
    return (T)ourFlags.get(flag.toString());
  }

  public static boolean is(@NotNull Key<Boolean> flag) {
    return get(flag) == Boolean.TRUE;
  }

  public static void addListener(@NotNull TestModeFlagListener listener) {
    ourListeners.add(listener);
  }
}
