// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
   * Called after all application startup tasks, including opening projects, are processed
   * (i.e., either completed or running in the background).
   * <p>
   * <b>NOTE:</b> Plugins must use {@link com.intellij.openapi.startup.ProjectActivity} and track successful once-per-application run instead.
   */
  @ApiStatus.Internal
  default void appStarted() { }

  /**
   * Called when a project frame is closed.
   */
  default void projectFrameClosed() { }

  /**
   * Called if the project opening was canceled or failed because of an error.
   */
  default void projectOpenFailed() { }

  /**
   * Fired before saving settings and before the final "can exit?" check.
   * The application may end up not closing if any of the
   * {@link com.intellij.openapi.application.ApplicationListener} listeners return false from their {@code canExitApplication}
   * method.
   */
  default void appClosing() { }

  /**
   * Fired after the final "can exit?" check, but before saving settings.
   */
  @ApiStatus.Internal
  default void beforeAppWillBeClosed(boolean isRestart) { }

  /**
   * Fired after saving settings and after the final "can exit?" check.
   */
  default void appWillBeClosed(boolean isRestart) { }
}