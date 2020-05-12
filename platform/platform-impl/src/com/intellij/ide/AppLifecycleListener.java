// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Listener for application lifecycle events.
 */
public interface AppLifecycleListener {
  @Topic.AppLevel
  Topic<AppLifecycleListener> TOPIC = new Topic<>(AppLifecycleListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  /** @deprecated use {@link #appFrameCreated(List)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  default void appFrameCreated(@NotNull List<String> commandLineArgs, @SuppressWarnings("unused") @NotNull Ref<? super Boolean> willOpenProject) {
    appFrameCreated(commandLineArgs);
  }

  /**
   * Called before an application frame is shown.
   */
  default void appFrameCreated(@NotNull List<String> commandLineArgs) {
    appUiReady();
  }

  /**
   * Called when the welcome screen is displayed (not called if the application opens a project).
   */
  default void welcomeScreenDisplayed() {
    appUiReady();
  }

  /**
   * Called when either a welcome screen or a project frame is displayed.
   */
  default void appUiReady() { }

  /**
   * Called after an application frame is shown.
   */
  default void appStarting(@Nullable Project projectFromCommandLine) { }

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

  /** @deprecated please use {@link AppLifecycleListener} directly */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  abstract class Adapter implements AppLifecycleListener { }
}