// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for application events.
 */
public interface ApplicationListener extends EventListener {
  /**
   * This method is called to check whether the Application is ready to exit.
   * @return true or false
   */
  default boolean canExitApplication() {
    return true;
  }

  /**
   * @deprecated Use {@link com.intellij.ide.AppLifecycleListener#appWillBeClosed(boolean)}
   */
  @Deprecated
  default void applicationExiting() {
  }

  /**
   * Is called before the {@code action} is started, when the write-lock is not acquired yet.
   */
  default void beforeWriteActionStart(@NotNull Object action) {
  }

  /**
   * Is called before the {@code action} is started, when the write-lock is acquired.
   */
  default void writeActionStarted(@NotNull Object action) {
  }

  /**
   * Is called after the {@code action} executed, while the write-lock is still acquired.
   */
  default void writeActionFinished(@NotNull Object action) {
  }

  /**
   * Is called after {@code action} is finished and the write-lock is released.
   */
  default void afterWriteActionFinished(@NotNull Object action) {
  }
}