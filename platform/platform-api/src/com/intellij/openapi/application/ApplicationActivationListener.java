/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.messages.Topic;

/**
 * @author yole
 */
public interface ApplicationActivationListener {
  Topic<ApplicationActivationListener> TOPIC = Topic.create("Application activation notifications", ApplicationActivationListener.class);

  /**
   * Called when app is activated by transferring focus to it.
   */
  default void applicationActivated(IdeFrame ideFrame) {
  }

  /**
   * Called when app is de-activated by transferring focus from it.
   */
  default void applicationDeactivated(IdeFrame ideFrame) {
  }

  /**
   * This is more precise notification than {code applicationDeactivated} callback.
   * It is intended for focus subsystem and purposes where we do not want
   * to be bothered by false application deactivation events.
   *
   * The shortcoming of the method is that a notification is delivered
   * with a delay. See {code app.deactivation.timeout} key in the registry
   */
  default void delayedApplicationDeactivated(IdeFrame ideFrame) {
  }

  /**
   * @deprecated Use {@link ApplicationActivationListener} instead
   */
  @Deprecated
  abstract class Adapter implements ApplicationActivationListener {
  }
}
