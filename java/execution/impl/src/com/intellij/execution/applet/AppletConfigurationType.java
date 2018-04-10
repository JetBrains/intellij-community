// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.applet;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AppletConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;

  AppletConfigurationType() {
    myFactory = new ConfigurationFactoryEx(this) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new AppletConfiguration(project, this);
      }

      @Override
      public void onNewConfigurationCreated(@NotNull RunConfiguration configuration) {
        ((ModuleBasedConfiguration)configuration).onNewConfigurationCreated();
      }
    };
  }

  @Override
  public String getDisplayName() {
    return ExecutionBundle.message("applet.configuration.name");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("applet.configuration.description");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.RunConfigurations.Applet;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @Override
  @NotNull
  public String getId() {
    return "Applet";
  }

  public static AppletConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AppletConfigurationType.class);
  }
}
