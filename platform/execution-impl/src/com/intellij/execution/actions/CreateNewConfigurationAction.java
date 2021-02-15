// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import org.jetbrains.annotations.Nullable;

public class CreateNewConfigurationAction extends CreateAction {
  @Nullable
  @Override
  protected RunnerAndConfigurationSettings findExisting(ConfigurationContext context) {
    return null;
  }

  @Override
  protected boolean isEnabledFor(RunConfiguration configuration,
                                 ConfigurationContext context) {
    return super.isEnabledFor(configuration, context) &&
           RunNewConfigurationContextAction.isNewConfiguration(configuration, context);
  }
}
