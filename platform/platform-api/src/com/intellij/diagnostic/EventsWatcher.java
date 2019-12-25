// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@ApiStatus.Experimental
public interface EventsWatcher {

  @NotNull
  NotNullLazyValue<Boolean> IS_ENABLED = NotNullLazyValue.createValue(
    () -> Boolean.getBoolean("idea.event.queue.dispatch.listen")
  );

  static boolean isEnabled() {
    return IS_ENABLED.getValue();
  }

  @Nullable
  static EventsWatcher getInstance() {
    if (!isEnabled()) return null;

    Application application = ApplicationManager.getApplication();
    if (application.isDisposed()) return null;

    application.assertIsDispatchThread();
    return application.getService(EventsWatcher.class);
  }

  void runnableStarted(@NotNull Runnable runnable);

  void runnableFinished(@NotNull Runnable runnable, long startedAt);

  void edtEventStarted(@NotNull AWTEvent event);

  void edtEventFinished(@NotNull AWTEvent event, long startedAt);
}
