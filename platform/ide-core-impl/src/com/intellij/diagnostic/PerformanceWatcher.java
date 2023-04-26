// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

public abstract class PerformanceWatcher implements Disposable {
  public static final String DUMP_PREFIX = "threadDump-";

  private static final Supplier<@Nullable PerformanceWatcher> ourInstance = CachedSingletonsRegistry.lazy(() -> {
    return ApplicationManager.getApplication().getService(PerformanceWatcher.class);
  });

  @ApiStatus.Internal
  public static @Nullable PerformanceWatcher getInstanceOrNull() {
    return LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred() ? ourInstance.get() : null;
  }

  public static @NotNull PerformanceWatcher getInstance() {
    LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred();
    return Objects.requireNonNull(ourInstance.get());
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

  public abstract void processUnfinishedFreeze(@NotNull ObjIntConsumer<Path> consumer);

  public abstract int getDumpInterval();

  public abstract int getUnresponsiveInterval();

  public abstract int getMaxDumpDuration();

  public abstract @Nullable String getJitProblem();

  public abstract void clearFreezeStacktraces();

  @ApiStatus.Internal
  public abstract void edtEventStarted();

  @ApiStatus.Internal
  public abstract void edtEventFinished();

  /**
   * @deprecated use {@link #dumpThreads(String, boolean, boolean)} instead
   */
  @Deprecated
  public @Nullable Path dumpThreads(@NotNull String pathPrefix, boolean appendMillisecondsToFileName) {
    return dumpThreads(pathPrefix, appendMillisecondsToFileName, false);
  }

  /**
   * @param stripDump if set to true, then some information in the dump that is considered useless for debugging
   *                  might be omitted. This should significantly reduce the size of the dump.
   *                  <p>
   *                  For example, some stackframes that correspond to {@code kotlinx.coroutines}
   *                  library internals might be omitted.
   */
  public abstract @Nullable Path dumpThreads(@NotNull String pathPrefix, boolean appendMillisecondsToFileName, boolean stripDump);

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
