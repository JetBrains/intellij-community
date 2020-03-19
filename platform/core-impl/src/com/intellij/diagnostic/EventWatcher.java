// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.util.ui.EDT.assertIsEdt;

@ApiStatus.Experimental
public interface EventWatcher {

  @NotNull
  NotNullLazyValue<Boolean> IS_ENABLED = NotNullLazyValue.createValue(
    () -> Boolean.getBoolean("idea.event.queue.dispatch.listen")
  );

  static boolean isEnabled() {
    return IS_ENABLED.getValue();
  }

  @Nullable
  static EventWatcher getInstance() {
    if (!isEnabled()) return null;

    Application application = getApplication();
    if (application.isDisposed()) return null;

    assertIsEdt();
    return application.getService(EventWatcher.class);
  }

  void runnableStarted(@NotNull Runnable runnable, long startedAt);

  void runnableFinished(@NotNull Runnable runnable, long startedAt);

  void edtEventStarted(@NotNull AWTEvent event);

  void edtEventFinished(@NotNull AWTEvent event, long startedAt);

  void logTimeMillis(@NotNull String processId, long startedAt,
                     @NotNull Class<? extends Runnable> runnableClass);

  default void logTimeMillis(@NotNull String processId, long startedAt) {
    logTimeMillis(processId, startedAt, Runnable.class);
  }
}
