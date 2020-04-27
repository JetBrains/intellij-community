// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface LoggableEventWatcher extends EventWatcher {

  default void logTimeMillis(@NotNull String processId, long startedAt) {
    logTimeMillis(processId, startedAt, Runnable.class);
  }

  void logTimeMillis(@NotNull String processId, long startedAt,
                     @NotNull Class<? extends Runnable> runnableClass);
}
