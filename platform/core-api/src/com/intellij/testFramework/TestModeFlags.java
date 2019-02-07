// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provides a centralized place for changing the behavior of the IDE in tests so that specific
 * features can be tested.
 *
 * @author yole
 */
public class TestModeFlags {
  private static final HashMap<String, Object> ourFlags = new HashMap<>();
  private static final List<TestModeFlagListener> ourListeners = new CopyOnWriteArrayList<>();

  @TestOnly
  public static <T> T set(Key<T> flag, T value) {
    //noinspection unchecked
    T oldValue = (T)ourFlags.get(flag.toString());
    ourFlags.put(flag.toString(), value);
    for (TestModeFlagListener listener : ourListeners) {
      listener.testModeFlagChanged(flag, value);
    }
    return oldValue;
  }

  @TestOnly
  public static void reset(Key<?> flag) {
    set(flag, null);
  }

  @TestOnly
  public static <T> void set(Key<T> flag, T value, Disposable parentDisposable) {
    T oldValue = get(flag);
    set(flag, value);
    Disposer.register(parentDisposable, () -> {
      set(flag, oldValue);
    });
  }

  public static <T> T get(Key<T> flag) {
    //noinspection unchecked
    return (T)ourFlags.get(flag.toString());
  }

  public static boolean is(Key<Boolean> flag) {
    return get(flag) == Boolean.TRUE;
  }

  public static void addListener(TestModeFlagListener listener) {
    ourListeners.add(listener);
  }
}
