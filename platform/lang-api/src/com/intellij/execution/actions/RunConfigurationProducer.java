/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class RunConfigurationProducer {
  public static ExtensionPointName<RunConfigurationProducer> EP_NAME = ExtensionPointName.create("com.intellij.runConfigurationProducer");
  private final ConfigurationType myConfigurationType;

  protected RunConfigurationProducer(final ConfigurationType configurationType) {
    myConfigurationType = configurationType;
  }

  public ConfigurationType getConfigurationType() {
    return myConfigurationType;
  }

  @Nullable
  public abstract ConfigurationFromContext createConfigurationFromContext(ConfigurationContext context);

  public abstract boolean isConfigurationFromContext(RunConfiguration configuration, ConfigurationContext context);

  @Nullable
  public RunnerAndConfigurationSettings findExistingConfiguration(ConfigurationContext context) {
    final RunManager runManager = RunManager.getInstance(context.getProject());
    final RunnerAndConfigurationSettings[] configurations = runManager.getConfigurationSettings(myConfigurationType);
    for (RunnerAndConfigurationSettings configurationSettings : configurations) {
      if (isConfigurationFromContext(configurationSettings.getConfiguration(), context)) {
        return configurationSettings;
      }
    }
    return null;
  }
}
