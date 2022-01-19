// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface EventWatcher {
  static boolean isEnabled() {
    return InstanceHolder.isEnabled;
  }

  static @Nullable EventWatcher getInstanceOrNull() {
    if (!isEnabled()) {
      return null;
    }

    EventWatcher result = InstanceHolder.instance;
    if (result == null && LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred()) {
      Application app = ApplicationManager.getApplication();
      if (app != null && !app.isDisposed()) {
        result = app.getService(EventWatcher.class);
        InstanceHolder.instance = result;
      }
    }
    return result;
  }

  @RequiresEdt
  void runnableStarted(@NotNull Runnable runnable, long startedAt);

  @RequiresEdt
  void runnableFinished(@NotNull Runnable runnable, long finishedAt);

  @RequiresEdt
  void edtEventStarted(@NotNull AWTEvent event, long startedAt);

  @RequiresEdt
  void edtEventFinished(@NotNull AWTEvent event, long finishedAt);

  void reset();

  void logTimeMillis(@NotNull String processId,
                     long startedAt,
                     @NotNull Class<? extends Runnable> runnableClass);

  default void logTimeMillis(@NotNull String processId, long startedAt) {
    logTimeMillis(processId, startedAt, Runnable.class);
  }
}

final class InstanceHolder {
  static EventWatcher instance;

  static final boolean isEnabled = Boolean.getBoolean("idea.event.queue.dispatch.listen");

  private InstanceHolder() { }
}