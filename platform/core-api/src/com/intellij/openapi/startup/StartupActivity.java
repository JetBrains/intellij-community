// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.startup;

import com.intellij.ide.util.RunOnceUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Runs an activity on project open.
 * <p>
 * If activity implements {@link com.intellij.openapi.project.DumbAware}, it is executed after project is opened
 * on a background thread with no visible progress indicator. Otherwise, it is executed on EDT when indexes are ready.
 * </p>
 * <p>
 * See <a href="https://github.com/JetBrains/intellij-community/blob/master/platform/service-container/overview.md#startup-activity">docs</a> for details.
 *
 * @see StartupManager
 * @see RunOnceUtil
 */
public interface StartupActivity {

  ExtensionPointName<StartupActivity> POST_STARTUP_ACTIVITY = new ExtensionPointName<>("com.intellij.postStartupActivity");

  void runActivity(@NotNull Project project);

  /**
   * Represents a startup activity that should be executed before {@link com.intellij.openapi.project.DumbService} switches to the "smart mode".
   */
  interface RequiredForSmartMode extends StartupActivity {
  }

  interface DumbAware extends StartupActivity, com.intellij.openapi.project.DumbAware {
  }

  interface Background extends StartupActivity, com.intellij.openapi.project.DumbAware {
  }
}
