// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  final class InstanceHolder {
    private @Nullable EventWatcher myInstance = null;
    private final boolean myIsEnabled = Boolean.getBoolean("idea.event.queue.dispatch.listen");

    private InstanceHolder() {}
  }

  @NotNull InstanceHolder ourInstance = new InstanceHolder();

  static boolean isEnabled() {
    return ourInstance.myIsEnabled;
  }

  static @Nullable EventWatcher getInstanceOrNull() {
    if (!isEnabled()) return null;

    EventWatcher result = ourInstance.myInstance;
    if (result == null) {
      Application application = ApplicationManager.getApplication();
      if (application != null &&
          !application.isDisposed() &&
          LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred()) {
        ourInstance.myInstance = result = application.getService(EventWatcher.class);
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
