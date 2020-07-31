// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.diagnostic.PluginException;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ThrowableDescription {
  private static final String UNKNOWN = "unknown";
  private static final String THIRD_PARTY = "third.party";

  @Nullable private final Throwable myThrowable;
  private final StackTraceElement @Nullable [] myStacktrace;

  public ThrowableDescription(@Nullable Throwable throwable) {
    myThrowable = throwable != null ? getCause(throwable) : null;
    myStacktrace = myThrowable != null ? getStacktrace(myThrowable) : null;
  }

  private static StackTraceElement @Nullable [] getStacktrace(@NotNull Throwable throwable) {
    try {
      return throwable.getStackTrace();
    }
    catch (Throwable e) {
      // Kotlin internals might throw an exception and it doesn't support retrieving a stacktrace
      return null;
    }
  }

  @NotNull
  public String getClassName() {
    if (myThrowable == null) {
      return UNKNOWN;
    }

    final Class<?> throwableClass = myThrowable.getClass();
    final PluginInfo throwableLocation = PluginInfoDetectorKt.getPluginInfo(throwableClass);
    return (throwableLocation.isSafeToReport()) ? throwableClass.getName() : THIRD_PARTY;
  }

  public int getSize() {
    if (myStacktrace == null) {
      return -1;
    }

    return myStacktrace.length;
  }

  @NotNull
  public List<String> getLastFrames(int frameCount) {
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

  @NotNull
  private static Throwable getCause(@NotNull Throwable throwable) {
    final boolean isPluginException = throwable instanceof PluginException && throwable.getCause() != null;
    return isPluginException ? throwable.getCause() : throwable;
  }
}
