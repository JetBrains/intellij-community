// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.startup;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * <p>Runs an activity on project open.</p>
 * See https://github.com/JetBrains/intellij-community/blob/master/platform/service-container/overview.md#startup-activity.
 *
 * @see com.intellij.openapi.startup.StartupManager
 */
public interface StartupActivity {
  /**
   * Activity is executed on a pooled thread under 'Loading Project' dialog
   * after {@link com.intellij.openapi.components.ProjectComponent} initializations.
   * <p>
   * This extension point can be only used by platform itself.
   *
   * @see StartupManager#registerStartupActivity
   */
  @ApiStatus.Internal
  ExtensionPointName<StartupActivity> STARTUP_ACTIVITY = new ExtensionPointName<>("com.intellij.startupActivity");

  /**
   * If activity implements {@link com.intellij.openapi.project.DumbAware}, it is executed after project is opened on a background thread with no visible progress indicator.
   * Otherwise it is executed on EDT when indexes are ready.
   *
   * @see StartupManager#registerPostStartupActivity
   * @see DumbAware
   */
  ExtensionPointName<StartupActivity> POST_STARTUP_ACTIVITY = new ExtensionPointName<>("com.intellij.postStartupActivity");

  /**
   * Acts as {@link #POST_STARTUP_ACTIVITY}, but executed with 5 seconds delay after project opening.
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

  interface Background extends StartupActivity, com.intellij.openapi.project.DumbAware {
  }
}