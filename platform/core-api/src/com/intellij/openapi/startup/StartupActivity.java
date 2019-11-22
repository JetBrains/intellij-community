// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.startup;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * <p>Runs an activity on project open.</p>
 *
 * <p>If the activity implements {@link com.intellij.openapi.project.DumbAware} interface, e.g. {@link DumbAware}, it will be started in a pooled thread
 * under 'Loading Project' dialog, otherwise it will be started in the dispatch thread after the initialization.</p>
 *
 * @author Dmitry Avdeev
 */
public interface StartupActivity {
  ExtensionPointName<StartupActivity> POST_STARTUP_ACTIVITY = ExtensionPointName.create("com.intellij.postStartupActivity");

  /**
   * <p>Executed some time after startup on a background thread with no visible progress indicator. Such activities may produce
   * notifications but should not be used for any work that needs to be otherwise visible to users (including work that consumes
   * CPU over a noticeable period).</p>
   *
   * <p>Such activities are run regardless of the current indexing mode and should not be used for any work that requires access
   * to indices. The current project may get disposed while the activity is running, and the activity may not be interrupted
   * immediately when this happens, so if you need to access other components, you're responsible for doing this in a
   * thread-safe way (e.g. by taking a read action to collect all the state you need).</p>
   */
  ExtensionPointName<StartupActivity.Background> BACKGROUND_POST_STARTUP_ACTIVITY = ExtensionPointName.create("com.intellij.backgroundPostStartupActivity");

  void runActivity(@NotNull Project project);

  interface DumbAware extends StartupActivity, com.intellij.openapi.project.DumbAware {
  }

  interface Background extends StartupActivity {}
}