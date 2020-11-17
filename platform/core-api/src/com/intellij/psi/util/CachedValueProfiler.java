// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@ApiStatus.Internal
public final class CachedValueProfiler {
  private static final CachedValueProfiler ourInstance = new CachedValueProfiler();
  private static final boolean ourCanProfile = ApplicationManager.getApplication().isInternal();

  private final Object myLock = new Object();

  private volatile MultiMap<StackTraceElement, Info> myStorage;
  private volatile ConcurrentMap<CachedValueProvider.Result<?>, Info> myTemporaryResults;

  public static boolean canProfile() {
    return ourCanProfile;
  }

  public boolean isEnabled() {
    return myStorage != null;
  }

  public void setEnabled(boolean value) {
    synchronized (myLock) {
      if (value) {
        MultiMap<StackTraceElement, Info> storage = myStorage;
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
    MultiMap<StackTraceElement, Info> storage = myStorage;
    if (storage == null) return;

    ConcurrentMap<CachedValueProvider.Result<?>, Info> temporaryResults = myTemporaryResults;
    if (temporaryResults == null) return;

    StackTraceElement origin = findOrigin();
    if (origin == null) return;

    Info info = new Info(origin);
    storage.putValue(origin, info);
    temporaryResults.put(result, info);
  }

  public @Nullable <T> Info getTemporaryInfo(@NotNull CachedValueProvider.Result<T> result) {
    ConcurrentMap<CachedValueProvider.Result<?>, Info> map = myTemporaryResults;
    return map != null ? map.remove(result) : null;
  }

  public MultiMap<StackTraceElement, Info> getStorageSnapshot() {
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

  public static class Info extends AtomicLong {
    private final long myCreatedTimeStamp;
    private volatile long myInvalidatedTimeStamp = -1;

    private final StackTraceElement myOrigin;

    public Info(@NotNull StackTraceElement origin) {
      myCreatedTimeStamp = currentTime();
      myOrigin = origin;
    }

    public void invalidate() {
      long cur = myInvalidatedTimeStamp;
      myInvalidatedTimeStamp = cur == -1 ? currentTime() : cur;
    }

    public void valueUsed() {
      incrementAndGet();
    }

    public long getUseCount() {
      return get();
    }

    public long getLifetime() {
      long disposedTime = myInvalidatedTimeStamp;
      if (disposedTime == -1) disposedTime = currentTime();

      return disposedTime - myCreatedTimeStamp;
    }

    @NotNull
    public StackTraceElement getOrigin() {
      return myOrigin;
    }

    private static long currentTime() {
      return System.currentTimeMillis();
    }
  }
}
