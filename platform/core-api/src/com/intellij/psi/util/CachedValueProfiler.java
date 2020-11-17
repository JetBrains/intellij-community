// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CachedValueProfiler {
  private static final CachedValueProfiler ourInstance = new CachedValueProfiler();
  private static final boolean ourCanProfile = ApplicationManager.getApplication().isInternal();

  private final Object myLock = new Object();

  private volatile MultiMap<StackTraceElement, ProfilingInfo> myStorage;
  private volatile ConcurrentMap<CachedValueProvider.Result<?>, ProfilingInfo> myTemporaryResults;

  public static boolean canProfile() {
    return ourCanProfile;
  }

  public boolean isEnabled() {
    return myStorage != null;
  }

  public void setEnabled(boolean value) {
    synchronized (myLock) {
      if (value) {
        MultiMap<StackTraceElement, ProfilingInfo> storage = myStorage;
        if (storage == null) {
          myStorage = MultiMap.createConcurrent();
        }
        myTemporaryResults = new ConcurrentHashMap<>();
      }
      else {
        myStorage = null;
        myTemporaryResults = null;
      }
    }
  }

  public static @NotNull CachedValueProfiler getInstance() {
    return ourInstance;
  }

  public void createInfo(@NotNull CachedValueProvider.Result<?> result) {
    MultiMap<StackTraceElement, ProfilingInfo> storage = myStorage;
    if (storage == null) return;

    ConcurrentMap<CachedValueProvider.Result<?>, ProfilingInfo> temporaryResults = myTemporaryResults;
    if (temporaryResults == null) return;

    StackTraceElement origin = findOrigin();
    if (origin == null) return;

    ProfilingInfo info = new ProfilingInfo(origin);
    storage.putValue(origin, info);
    temporaryResults.put(result, info);
  }

  public @Nullable <T> ProfilingInfo getTemporaryInfo(@NotNull CachedValueProvider.Result<T> result) {
    ConcurrentMap<CachedValueProvider.Result<?>, ProfilingInfo> map = myTemporaryResults;
    return map != null ? map.remove(result) : null;
  }

  public MultiMap<StackTraceElement, ProfilingInfo> getStorageSnapshot() {
    return myStorage.copy();
  }

  private static @Nullable StackTraceElement findOrigin() {
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    for (int i = 3, len = stackTrace.length; i < len; i++) {
      String className = stackTrace[i].getClassName();
      if (className.startsWith("com.intellij.util.CachedValue")) continue;
      if (className.startsWith("com.intellij.psi.util.CachedValue")) continue;
      if (className.startsWith("com.intellij.psi.impl.PsiCachedValue")) continue;
      if (className.startsWith("com.intellij.openapi.util.Recursion")) continue;
      return stackTrace[i];
    }
    return null;
  }
}
