// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Mapping paths from Windows to WSL requires obtaining mount a point in the WSL filesystem. To detect one, we are executing {@code pwd}
 * command. Result is cached and computed lazily, so this may happen in any time. In some cases on EDT and under WriteAction. Executions
 * on such circumstances are forbidden and still, can't avoided by any other means, so this is a situational fix with pre-initialization
 * of mount roots for all distributions available at application startup.
 */
public class WslInitializationStartupActivity implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    WSLUtil.getAvailableDistributions().forEach(it -> it.getWslPath("C:\\"));
  }
}
