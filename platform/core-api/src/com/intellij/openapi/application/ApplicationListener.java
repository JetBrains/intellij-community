// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
   * Is called when application is exiting.
   * Consider to use {@link com.intellij.ide.AppLifecycleListener#appWillBeClosed(boolean)}
   */
  default void applicationExiting() {
  }

  /**
   * Is called before action start.
   */
  default void beforeWriteActionStart(@NotNull Object action) {
  }

  /**
   * Is called on action start.
   */
  default void writeActionStarted(@NotNull Object action) {
  }

  /**
   * Is called on before action finish, while while lock is still being hold
   */
  default void writeActionFinished(@NotNull Object action) {
  }

  /**
   * Is called after action finish and lock is released
   */
  default void afterWriteActionFinished(@NotNull Object action) {
  }
}