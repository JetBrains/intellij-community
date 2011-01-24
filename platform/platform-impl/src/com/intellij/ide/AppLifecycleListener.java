/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author yole
 */
public interface AppLifecycleListener {
  Topic<AppLifecycleListener> TOPIC = Topic.create("Application lifecycle notifications", AppLifecycleListener.class);

  void appFrameCreated(final String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject);
  void appStarting(Project projectFromCommandLine);
  void projectFrameClosed();
  void projectOpenFailed();
  void welcomeScreenDisplayed();

  /**
   * Fired before saving settings and before final 'can exit?' check. App may end up not closing if some of the 'can exit?' listeners
   * return false.
   */
  void appClosing();

  abstract class Adapter implements AppLifecycleListener {
    public void appFrameCreated(final String[] commandLineArgs, @NotNull final Ref<Boolean> willOpenProject) {
    }

    public void appStarting(final Project projectFromCommandLine) {
    }

    public void projectFrameClosed() {
    }

    public void projectOpenFailed() {
    }

    public void welcomeScreenDisplayed() {
    }

    public void appClosing() {
    }
  }
}