// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ui.content.Content;

/**
 * @author konstantin.aleev
 */
public interface RunDashboardRunConfigurationNode {

  @Deprecated(forRemoval = true)
  Content getContent();

  RunContentDescriptor getDescriptor();

  @Deprecated(forRemoval = true)
  RunnerAndConfigurationSettings getConfigurationSettings();
}
