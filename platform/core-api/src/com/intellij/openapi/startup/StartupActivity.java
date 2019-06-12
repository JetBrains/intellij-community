/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.openapi.startup;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Runs an activity on project open.
 * <p>
 * If the activity implements {@link com.intellij.openapi.project.DumbAware} it will be started in a pooled thread under 'Loading Project' dialog,
 * otherwise it will be started in the dispatch thread after the initialization.
 *
 * @author Dmitry Avdeev
 */
public interface StartupActivity {

  ExtensionPointName<StartupActivity> POST_STARTUP_ACTIVITY = ExtensionPointName.create("com.intellij.postStartupActivity");

  /**
   * Executed some time after startup on a background thread with no visible progress indicator. Such activities may produce
   * notifications but should not be used for any work that needs to be otherwise visible to users (including work that consumes
   * CPU over a noticeable period of time).
   *
   * Such activities are run regardless of the current indexing mode and should not be used for any work that requires access
   * to indices. The current project may get disposed while the activity is running, and the activity may not be interrupted
   * immediately when this happens, so if you need to access other components, you're responsible for doing this in a
   * thread-safe way (e.g. by taking a read action to collect all the state you need).
   */
  ExtensionPointName<StartupActivity> BACKGROUND_POST_STARTUP_ACTIVITY = ExtensionPointName.create("com.intellij.backgroundPostStartupActivity");

  void runActivity(@NotNull Project project);
}
