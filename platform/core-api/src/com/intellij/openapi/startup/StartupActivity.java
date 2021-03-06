// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.startup;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * <p>Runs an activity on project open.</p>
 * See <a href="https://github.com/JetBrains/intellij-community/blob/master/platform/service-container/overview.md#startup-activity">docs</a> for details.
 *
 * @see StartupManager
 */
public interface StartupActivity {
  /**
   * If activity implements {@link com.intellij.openapi.project.DumbAware}, it is executed after project is opened on a background thread with no visible progress indicator.
   * Otherwise it is executed on EDT when indexes are ready.
   *
   * @see StartupManager#registerPostStartupActivity
   * @see DumbAware
   */
  ExtensionPointName<StartupActivity> POST_STARTUP_ACTIVITY = new ExtensionPointName<>("com.intellij.postStartupActivity");

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