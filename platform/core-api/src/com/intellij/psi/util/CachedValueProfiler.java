// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@ApiStatus.Internal
public final class CachedValueProfiler {
  private static final CachedValueProfiler ourInstance = new CachedValueProfiler();
  private static final boolean ourCanProfile = ApplicationManager.getApplication().isInternal();

  private final Object myLock = new Object();

  private volatile MultiMap<StackTraceElement, Info> myStorage;
  private volatile ConcurrentMap<CachedValueProvider.Result<?>, Info> myTmpInfos;

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
          myTmpInfos = CollectionFactory.createConcurrentWeakMap();
        }
      }
      else {
        myStorage = null;
        myTmpInfos = null;
      }
    }
  }

  public static @NotNull CachedValueProfiler getInstance() {
    return ourInstance;
  }

  public void createInfo(@NotNull CachedValueProvider.Result<?> result) {
    if (myStorage == null) return;

    ConcurrentMap<CachedValueProvider.Result<?>, Info> map = myTmpInfos;
    if (map == null) return;

    StackTraceElement origin = findOrigin();
    if (origin == null) return;

    map.put(result, new Info(origin, -1, currentTime()));
  }

  public @Nullable Info storeInfo(@NotNull CachedValueProvider.Result<?> result, long startTime) {
    MultiMap<StackTraceElement, Info> storage = myStorage;
    if (storage == null) return null;

    ConcurrentMap<CachedValueProvider.Result<?>, Info> map = myTmpInfos;
    Info tmp = map != null ? map.remove(result) : null;
    if (tmp == null) return null;

    Info stored = new Info(tmp.origin, startTime, tmp.endTime);
    storage.putValue(tmp.getOrigin(), stored);
    return stored;
  }

  public MultiMap<StackTraceElement, Info> getStorageSnapshot() {
    return myStorage.copy();
  }

  private static @Nullable StackTraceElement findOrigin() {
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    int idx = 0;
    for (StackTraceElement e : stackTrace) {
      String method = e.getMethodName();
      String className = e.getClassName();
      if ("doCompute".equals(method) &&
          (className.endsWith("CachedValueImpl") || className.endsWith("CachedValue")) &&
          (className.startsWith("com.intellij.util.") || className.startsWith("com.intellij.psi."))) {
        break;
      }
      idx ++;
    }
    if (idx >= stackTrace.length) return null;
    for (int i = idx + 1; i >= 0; i --) {
      String className = stackTrace[i].getClassName();
      if (className.startsWith("com.intellij.util.CachedValue")) continue;
      if (className.startsWith("com.intellij.psi.util.CachedValue")) continue;
      if (className.startsWith("com.intellij.psi.impl.PsiCachedValue")) continue;
      if (className.startsWith("com.intellij.openapi.util.Recursion")) continue;
      return stackTrace[i];
    }
    return null;
  }

  public static long currentTime() {
    return System.currentTimeMillis();
  }

  public static final class Info extends AtomicLong {
    public final StackTraceElement origin;
    public final long startTime;
    public final long endTime;

    private volatile long myInvalidatedTime = -1;

    Info(@NotNull StackTraceElement origin, long startTime, long endTime) {
      this.origin = origin;
      this.startTime = startTime;
      this.endTime = endTime;
    }

    public void invalidate() {
      long cur = myInvalidatedTime;
      myInvalidatedTime = cur == -1 ? currentTime() : cur;
    }

    public void valueUsed() {
      incrementAndGet();
    }

    public long getUseCount() {
      return get();
    }

    public long getLifetime() {
      long disposedTime = myInvalidatedTime;
      if (disposedTime == -1) disposedTime = currentTime();

      return disposedTime - endTime;
    }

    public long getComputeTime() {
      return endTime - startTime;
    }

    @NotNull
    public StackTraceElement getOrigin() {
      return origin;
    }
  }
}
