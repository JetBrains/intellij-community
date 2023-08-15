// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.diagnostic.PluginException;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.diagnostic.UntraceableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ThrowableDescription {
  private static final String THIRD_PARTY = "third.party";

  private final @NotNull Throwable myThrowable;
  private final StackTraceElement @Nullable [] myStacktrace;

  public ThrowableDescription(@NotNull Throwable throwable) {
    myThrowable = getCause(throwable);
    myStacktrace = getStacktrace(myThrowable);
  }

  private static StackTraceElement @Nullable [] getStacktrace(@NotNull Throwable throwable) {
    return throwable instanceof UntraceableException ? null : throwable.getStackTrace();
  }

  public @NotNull Class<?> getThrowableClass() {
    return myThrowable.getClass();
  }

  public int getSize() {
    if (myStacktrace == null) {
      return -1;
    }

    return myStacktrace.length;
  }

  public @NotNull List<String> getLastFrames(int frameCount) {
    if (myStacktrace == null) {
      return Collections.emptyList();
    }

    int size = Math.min(myStacktrace.length, frameCount);
    List<String> result = new ArrayList<>(size);
    Map<String, PluginInfo> pluginInfoCache = new HashMap<>();

    for (int i = 0; i < size; i++) {
      StackTraceElement element = myStacktrace[i];

      PluginInfo pluginInfo = pluginInfoCache.get(element.getClassName());
      if (pluginInfo == null) {
        pluginInfo = PluginInfoDetectorKt.getPluginInfo(element.getClassName());
        pluginInfoCache.put(element.getClassName(), pluginInfo);
      }

      if (pluginInfo.isSafeToReport()) {
        result.add(element.getClassName() + "." + element.getMethodName());
      }
      else {
        result.add(THIRD_PARTY);
      }
    }

    return result;
  }

  private static @NotNull Throwable getCause(@NotNull Throwable throwable) {
    final boolean isPluginException = throwable instanceof PluginException && throwable.getCause() != null;
    return isPluginException ? throwable.getCause() : throwable;
  }
}
