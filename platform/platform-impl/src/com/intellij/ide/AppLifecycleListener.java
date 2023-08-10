// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Listener for application lifecycle events.
 */
public interface AppLifecycleListener {
  @Topic.AppLevel
  Topic<AppLifecycleListener> TOPIC = new Topic<>(AppLifecycleListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  /**
   * Called before an application frame is shown.
   */
  default void appFrameCreated(@NotNull List<String> commandLineArgs) { }

  /**
   * Called when the welcome screen is displayed (not called if the application opens a project).
   */
  default void welcomeScreenDisplayed() { }

  /**
   * Called after all application startup tasks, including opening projects, are processed (i.e. either completed or running in background).
   */
  @ApiStatus.Internal
  default void appStarted() { }

  /**
   * Called when a project frame is closed.
   */
  default void projectFrameClosed() { }

  /**
   * Called if the project opening was cancelled or failed because of an error.
   */
  default void projectOpenFailed() { }

  /**
   * Fired before saving settings and before final 'can exit?' check. App may end up not closing if some of the
   * {@link com.intellij.openapi.application.ApplicationListener} listeners return false from their {@code canExitApplication}
   * method.
   */
  default void appClosing() { }

  /**
   * Fired after saving settings and after final 'can exit?' check.
   */
  default void appWillBeClosed(boolean isRestart) { }
}