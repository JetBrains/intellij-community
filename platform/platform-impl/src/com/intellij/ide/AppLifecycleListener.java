/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Listener for application lifecycle events.
 *
 * @author yole
 */
public interface AppLifecycleListener {
  Topic<AppLifecycleListener> TOPIC = Topic.create("Application lifecycle notifications", AppLifecycleListener.class);

  default void appFrameCreated(final String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject) {
  }

  default void appStarting(@Nullable Project projectFromCommandLine) {
  }

  /**
   * Called when a project frame is closed.
   */
  default void projectFrameClosed() {
  }

  /**
   * Called if the project opening was cancelled or failed because of an error.
   */
  default void projectOpenFailed() {
  }

  /**
   * Called when the welcome screen is displayed.
   */
  default void welcomeScreenDisplayed() {
  }

  /**
   * Fired before saving settings and before final 'can exit?' check. App may end up not closing if some of the
   * {@link com.intellij.openapi.application.ApplicationListener} listeners return false from their {@code canExitApplication}
   * method.
   */
  default void appClosing() {
  }


  /**
   * Fired after saving settings and after final 'can exit?' check.
   */
  default void appWillBeClosed(boolean isRestart) {
  }

  /**
   * @deprecated Please use {@link AppLifecycleListener} directly.
   */
  @Deprecated
  abstract class Adapter implements AppLifecycleListener {
  }
}