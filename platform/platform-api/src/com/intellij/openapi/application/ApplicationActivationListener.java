// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author yole
 */
public interface ApplicationActivationListener {
  Topic<ApplicationActivationListener> TOPIC = Topic.create("Application activation notifications", ApplicationActivationListener.class);

  /**
   * Called when app is activated by transferring focus to it.
   */
  default void applicationActivated(@NotNull IdeFrame ideFrame) {
  }

  /**
   * Called when app is de-activated by transferring focus from it.
   */
  default void applicationDeactivated(@NotNull IdeFrame ideFrame) {
  }

  /**
   * @deprecated Use {@link #delayedApplicationDeactivated(Window)}
   */
  @Deprecated
  default void delayedApplicationDeactivated(@NotNull IdeFrame ideFrame) {
    delayedApplicationDeactivated((Window)ideFrame);
  }

  /**
   * This is more precise notification than {code applicationDeactivated} callback.
   * It is intended for focus subsystem and purposes where we do not want
   * to be bothered by false application deactivation events.
   * <p>
   * The shortcoming of the method is that a notification is delivered
   * with a delay. See {code app.deactivation.timeout} key in the registry
   */
  default void delayedApplicationDeactivated(@NotNull Window ideFrame) {
  }

  /**
   * @deprecated Use {@link ApplicationActivationListener} instead
   */
  @Deprecated
  abstract class Adapter implements ApplicationActivationListener {
  }
}
