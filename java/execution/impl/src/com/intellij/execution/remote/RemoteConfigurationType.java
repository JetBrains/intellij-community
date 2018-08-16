/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      @Override
      @NotNull
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new RemoteConfiguration(project, this);
      }

    };
  }

  @Override
  public String getDisplayName() {
    return ExecutionBundle.message("remote.debug.configuration.display.name");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("remote.debug.configuration.description");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.RunConfigurations.Remote;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @NotNull
  public ConfigurationFactory getFactory() {
    return myFactory;
  }

  @Override
  @NotNull
  public String getId() {
    return "Remote";
  }

  @NotNull
  public static RemoteConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(RemoteConfigurationType.class);
  }

}
