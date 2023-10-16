// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for Read Action life cycle.
 */
public interface ReadActionListener extends EventListener {
  /**
   * Is called before the {@code action} is started, when the read-lock is not acquired yet.
   */
  default void beforeReadActionStart(@NotNull Object action) {
  }

  /**
   * Is called before the {@code action} is started, when the read-lock is acquired.
   */
  default void readActionStarted(@NotNull Object action) {
  }

  /**
   * Is called after the {@code action} executed, while the read-lock is still acquired.
   */
  default void readActionFinished(@NotNull Object action) {
  }

  /**
   * Is called after {@code action} is finished and the read-lock is released.
   */
  default void afterReadActionFinished(@NotNull Object action) {
  }
}
