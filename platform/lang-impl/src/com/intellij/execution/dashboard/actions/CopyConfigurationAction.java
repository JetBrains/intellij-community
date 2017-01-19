/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.dashboard.DashboardRunConfigurationNode;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.util.PlatformIcons;

/**
 * @author konstantin.aleev
 */
public class CopyConfigurationAction extends RunConfigurationTreeAction {
  public CopyConfigurationAction() {
    super(ExecutionBundle.message("copy.configuration.action.name"),
          ExecutionBundle.message("copy.configuration.action.name"),
          PlatformIcons.COPY_ICON);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void doActionPerformed(DashboardRunConfigurationNode node) {
    RunManagerEx runManager = RunManagerEx.getInstanceEx(node.getProject());
    RunnerAndConfigurationSettings settings = node.getConfigurationSettings();

    RunnerAndConfigurationSettings copiedSettings = ((RunnerAndConfigurationSettingsImpl)settings).clone();
    runManager.setUniqueNameIfNeed(copiedSettings);
    copiedSettings.setFolderName(settings.getFolderName());

    final ConfigurationFactory factory = settings.getFactory();
    if (factory instanceof ConfigurationFactoryEx) {
      ((ConfigurationFactoryEx)factory).onConfigurationCopied(settings.getConfiguration());
    }

    runManager.addConfiguration(copiedSettings, runManager.isConfigurationShared(settings),
                                runManager.getBeforeRunTasks(settings.getConfiguration()), false);
  }
}
