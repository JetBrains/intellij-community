// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for Write Action life cycle.
 * You can register this listener with {@link com.intellij.openapi.application.ex.ApplicationEx#addWriteActionListener(WriteActionListener, Disposable)}
 */
public interface WriteActionListener extends EventListener {
  /**
   * Is called before the {@code action} is started, when the write-lock is not acquired yet.
   */
  default void beforeWriteActionStart(@NotNull Class<?> action) {
  }

  /**
   * Is called before the {@code action} is started, when the write-lock is acquired.
   */
  default void writeActionStarted(@NotNull Class<?> action) {
  }

  /**
   * Is called after the {@code action} executed, while the write-lock is still acquired.
   */
  default void writeActionFinished(@NotNull Class<?> action) {
  }

  /**
   * Is called after {@code action} is finished and the write-lock is released.
   */
  default void afterWriteActionFinished(@NotNull Class<?> action) {
  }
}
