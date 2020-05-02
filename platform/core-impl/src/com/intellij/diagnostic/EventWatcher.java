// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.application.Application;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

@ApiStatus.Experimental
public interface EventWatcher {

  final class InstanceHolder {
    private @Nullable EventWatcher myInstance = null;
    private final boolean myIsEnabled = Boolean.getBoolean("idea.event.queue.dispatch.listen");

    private InstanceHolder() {}
  }

  @NotNull InstanceHolder HOLDER = new InstanceHolder();

  static boolean isEnabled() {
    return HOLDER.myIsEnabled;
  }

  @Nullable
  static EventWatcher getInstance() {
    if (!isEnabled()) return null;

    EventWatcher result = HOLDER.myInstance;
    if (result != null) return result;

    Application application = getApplication();
    if (application == null || application.isDisposed()) return null;

    HOLDER.myInstance = result = application.getService(EventWatcher.class);

    return result;
  }

  void runnableStarted(@NotNull Runnable runnable, long startedAt);

  void runnableFinished(@NotNull Runnable runnable, long startedAt);

  void edtEventStarted(@NotNull AWTEvent event);

  void edtEventFinished(@NotNull AWTEvent event, long startedAt);

  void lockAcquired(@NotNull String invokedClassFqn, @NotNull LockKind lockKind);

  void logTimeMillis(@NotNull String processId, long startedAt,
                     @NotNull Class<? extends Runnable> runnableClass);

  default void logTimeMillis(@NotNull String processId, long startedAt) {
    logTimeMillis(processId, startedAt, Runnable.class);
  }
}
