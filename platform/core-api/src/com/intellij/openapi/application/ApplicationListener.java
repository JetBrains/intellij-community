// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.ApiStatus;
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
   * This method is called before restarting an application (e.g., after installing a plugin). 
   * Return {@code true} if the application can be restarted, or {@code false} to cancel restarting.
   */
  default boolean canRestartApplication() {
    return canExitApplication();
  }

  /**
   * @deprecated Use {@link com.intellij.ide.AppLifecycleListener#appWillBeClosed(boolean)}
   */
  @Deprecated
  default void applicationExiting() {
  }

  /**
   * Is called before the {@code action} is started, when the write-lock is not acquired yet.
   * <p>
   * <b>Obsolescence note:</b> Consider using {@link com.intellij.openapi.application.WriteActionListener} instead
   */
  @ApiStatus.Obsolete
  default void beforeWriteActionStart(@NotNull Object action) {
  }


  /**
   * Is called before the {@code action} is started, when the write-lock is acquired.
   * <p>
   * <b>Obsolescence note:</b> Consider using {@link com.intellij.openapi.application.WriteActionListener} instead
   */
  @ApiStatus.Obsolete
  default void writeActionStarted(@NotNull Object action) {
  }

  /**
   * Is called after the {@code action} executed, while the write-lock is still acquired.
   * <p>
   * <b>Obsolescence note:</b> Consider using {@link com.intellij.openapi.application.WriteActionListener} instead
   */
  @ApiStatus.Obsolete
  default void writeActionFinished(@NotNull Object action) {
  }

  /**
   * Is called after {@code action} is finished and the write-lock is released.
   * <p>
   * <b>Obsolescence note:</b> Consider using {@link com.intellij.openapi.application.WriteActionListener} instead
   *
   */
  @ApiStatus.Obsolete
  default void afterWriteActionFinished(@NotNull Object action) {
  }
}