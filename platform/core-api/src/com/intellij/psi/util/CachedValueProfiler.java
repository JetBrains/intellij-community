// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.util.containers.ConcurrentMultiMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

  @NotNull
  public String dumpStorage() {
    List<TotalInfo> list = ContainerUtil.newArrayList();
    myStorage.entrySet().forEach((entry) -> list.add(new TotalInfo(entry.getKey(), entry.getValue())));

    Collections.sort(list, Comparator.comparing(info -> ((double)info.getTotalUseCount()) / info.getInfos().size()));

    StringBuilder builder = new StringBuilder();
    for (TotalInfo info : list) {
      builder
        .append(info.getOrigin()).append('\n')
        .append("                ").append("total lifetime: ").append(info.getTotalLifeTime()).append(" ms").append('\n')
        .append("                ").append("total use count: ").append(info.getTotalUseCount()).append('\n')
        .append("                ").append("created : ").append(info.getInfos().size()).append('\n')
        .append("                ").append("use count/created: ").append(((double)info.getTotalUseCount()) / info.getInfos().size()).append('\n')
        .append('\n');
    }

    return builder.toString();
  }

  private static class TotalInfo {
    private final StackTraceElement myOrigin;
    private final long myTotalLifeTime;
    private final long myTotalUseCount;

    private final List<ProfilingInfo> myInfos;

    public TotalInfo(StackTraceElement origin, Collection<ProfilingInfo> infos) {
      myOrigin = origin;
      myInfos = Collections.unmodifiableList(ContainerUtil.newArrayList(infos));

      myTotalLifeTime = myInfos.stream().mapToLong(value -> value.getLifetime()).sum();
      myTotalUseCount = myInfos.stream().mapToLong(value -> value.getUseCount()).sum();
    }

    public StackTraceElement getOrigin() {
      return myOrigin;
    }

    public long getTotalLifeTime() {
      return myTotalLifeTime;
    }

    public long getTotalUseCount() {
      return myTotalUseCount;
    }

    @NotNull
    public List<ProfilingInfo> getInfos() {
      return myInfos;
    }
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
