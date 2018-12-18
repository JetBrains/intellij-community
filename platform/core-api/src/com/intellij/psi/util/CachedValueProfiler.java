// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.util.containers.ConcurrentMultiMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CachedValueProfiler {
  private static final CachedValueProfiler ourInstance = new CachedValueProfiler();

  private volatile ConcurrentMultiMap<StackTraceElement, ProfilingInfo> myStorage = null;

  private final Object myLock = new Object();

  public boolean isEnabled() {
    return myStorage != null;
  }

  public void setEnabled(boolean value) {
    synchronized (myLock) {
      if (value) {
        ConcurrentMultiMap<StackTraceElement, ProfilingInfo> storage = myStorage;
        if (storage == null) {
          myStorage = new ConcurrentMultiMap<>();
        }
      }
      else {
        myStorage = null;
      }
    }
  }

  @NotNull
  public static CachedValueProfiler getInstance() {
    return ourInstance;
  }

  @Nullable
  public ProfilingInfo createInfo() {
    ConcurrentMultiMap<StackTraceElement, ProfilingInfo> storage = myStorage;
    if (storage == null) return null;

    StackTraceElement origin = findOrigin();
    if (origin == null) return null;

    ProfilingInfo info = new ProfilingInfo(origin);
    storage.putValue(origin, info);
    return info;
  }

  public MultiMap<StackTraceElement, ProfilingInfo> getStorageSnapshot() {
    return myStorage.copy();
  }

  @Nullable
  private static StackTraceElement findOrigin() {
    StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    return findFirstStackTraceElementExcluding(stackTrace, CachedValueProfiler.class.getName(), CachedValueProvider.class.getName());
  }

  @Nullable
  private static StackTraceElement findFirstStackTraceElementExcluding(@NotNull StackTraceElement[] stackTraceElements,
                                                                       @NotNull String... excludedClasses) {
    for (StackTraceElement element : stackTraceElements) {
      if (!matches(element, excludedClasses)) {
        return element;
      }
    }

    return null;
  }

  private static boolean matches(@NotNull StackTraceElement element, @NotNull String[] excludedClasses) {
    for (String aClass : excludedClasses) {
      if (element.getClassName().startsWith(aClass)) return true;
    }
    return false;
  }

}
