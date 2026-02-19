// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for Read Action life cycle.
 */
public interface ReadActionListener extends EventListener {
  /**
   * Is called before the {@code action} is started, when the read-lock is not acquired yet.
   */
  default void beforeReadActionStart(@NotNull Class<?> action) {
  }

  /**
   * Is called before the {@code action} is started, when the read-lock is acquired.
   */
  default void readActionStarted(@NotNull Class<?> action) {
  }

  /**
   * Is called when the fast path of lock acquisition failed.
   * It means that there is a (pending) write action in progress, so the lock acquisition will likely proceed with blocking the thread.
   */
  @ApiStatus.Internal
  default void fastPathAcquisitionFailed() {
  }

  /**
   * Is called after the {@code action} executed, while the read-lock is still acquired.
   */
  default void readActionFinished(@NotNull Class<?> action) {
  }

  /**
   * Is called after {@code action} is finished and the read-lock is released.
   */
  default void afterReadActionFinished(@NotNull Class<?> action) {
  }
}
