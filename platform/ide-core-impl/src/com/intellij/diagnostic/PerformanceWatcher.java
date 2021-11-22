// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

public abstract class PerformanceWatcher implements Disposable {
  public static final String DUMP_PREFIX = "threadDump-";

  protected static @Nullable PerformanceWatcher ourInstance = CachedSingletonsRegistry.markCachedField(PerformanceWatcher.class);

  @ApiStatus.Internal
  public static @Nullable PerformanceWatcher getInstanceOrNull() {
    PerformanceWatcher watcher = ourInstance;
    if (watcher == null && LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred()) {
      Application app = ApplicationManager.getApplication();
      if (app != null) {
        watcher = app.getServiceIfCreated(PerformanceWatcher.class);
      }
    }
    return watcher;
  }

  public static @NotNull PerformanceWatcher getInstance() {
    LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred();
    return ourInstance != null ?
           ourInstance :
           ApplicationManager.getApplication().getService(PerformanceWatcher.class);
  }

  public interface Snapshot {
    void logResponsivenessSinceCreation(@NonNls @NotNull String activityName);
    String getLogResponsivenessSinceCreationMessage(@NonNls @NotNull String activityName);
  }

  public abstract ScheduledExecutorService getExecutor();

  public static @NotNull Snapshot takeSnapshot() {
    return getInstance().newSnapshot();
  }

  protected abstract Snapshot newSnapshot();

  public abstract void processUnfinishedFreeze(@NotNull BiConsumer<? super File, ? super Integer> consumer);

  public abstract int getDumpInterval();

  public abstract int getUnresponsiveInterval();

  public abstract int getMaxDumpDuration();

  public abstract @Nullable String getJitProblem();

  public abstract void clearFreezeStacktraces();

  @ApiStatus.Internal
  public abstract void edtEventStarted();

  @ApiStatus.Internal
  public abstract void edtEventFinished();

  public abstract @Nullable File dumpThreads(@NotNull String pathPrefix, boolean appendMillisecondsToFileName);

  public static @NotNull String printStacktrace(@NotNull String headerMsg,
                                                @NotNull Thread thread,
                                                StackTraceElement @NotNull [] stackTrace) {
    @SuppressWarnings("NonConstantStringShouldBeStringBuffer")
    StringBuilder trace = new StringBuilder(
      headerMsg + thread + " (" + (thread.isAlive() ? "alive" : "dead") + ") " + thread.getState() + "\n--- its stacktrace:\n");
    for (final StackTraceElement stackTraceElement : stackTrace) {
      trace.append(" at ").append(stackTraceElement).append("\n");
    }
    trace.append("---\n");
    return trace.toString();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void dumpThreadsToConsole(@NonNls String message) {
    System.err.println(message);
    System.err.println(ThreadDumper.dumpThreadsToString());
  }
}
