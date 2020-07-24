// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 * See https://github.com/JetBrains/intellij-community/blob/master/platform/service-container/overview.md#startup-activity.
 */
public interface StartupActivity {
  ExtensionPointName<StartupActivity> POST_STARTUP_ACTIVITY = new ExtensionPointName<>("com.intellij.postStartupActivity");

  /**
   * Please see https://github.com/JetBrains/intellij-community/blob/master/platform/service-container/overview.md#startup-activity
   */
  ExtensionPointName<StartupActivity.Background> BACKGROUND_POST_STARTUP_ACTIVITY = new ExtensionPointName<>("com.intellij.backgroundPostStartupActivity");

  void runActivity(@NotNull Project project);

  /**
   * Represent a startup activity that should be executed before {@link com.intellij.openapi.project.DumbService} will be switched to "smart mode".
   */
  interface RequiredForSmartMode extends StartupActivity {
  }

  interface DumbAware extends StartupActivity, com.intellij.openapi.project.DumbAware {
  }

  interface Background extends StartupActivity, com.intellij.openapi.project.DumbAware {}
}