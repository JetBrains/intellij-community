// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for Write-Intent Read Action life cycle.
 */
public interface WriteIntentReadActionListener extends EventListener {
  /**
   * Is called before the {@code action} is started, when the write-intent read lock is not acquired yet.
   */
  default void beforeWriteIntentReadActionStart(@NotNull Class<?> action) {
  }

  /**
   * Is called before the {@code action} is started, when the write-intent read lock is acquired.
   */
  default void writeIntentReadActionStarted(@NotNull Class<?> action) {
  }

  /**
   * Is called after the {@code action} executed, while the write-intent read lock is still acquired.
   */
  default void writeIntentReadActionFinished(@NotNull Class<?> action) {
  }

  /**
   * Is called after {@code action} is finished and the write-intent read lock is released.
   */
  default void afterWriteIntentReadActionFinished(@NotNull Class<?> action) {
  }
}
