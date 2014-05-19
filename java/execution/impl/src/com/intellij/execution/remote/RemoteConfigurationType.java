/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Class RemoteConfigurationFactory
 * @author Jeka
 */
package com.intellij.execution.remote;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class RemoteConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;

  /**reflection*/
  public RemoteConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new RemoteConfiguration(project, this);
      }

    };
  }

  public String getDisplayName() {
    return ExecutionBundle.message("remote.debug.configuration.display.name");
  }

  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("remote.debug.configuration.description");
  }

  public Icon getIcon() {
    return AllIcons.RunConfigurations.Remote;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @NotNull
  public ConfigurationFactory getFactory() {
    return myFactory;
  }

  @NotNull
  public String getId() {
    return "Remote";
  }

  @NotNull
  public static RemoteConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(RemoteConfigurationType.class);
  }

}
