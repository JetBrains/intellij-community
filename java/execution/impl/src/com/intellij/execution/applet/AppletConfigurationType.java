// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.applet;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import com.intellij.util.LazyUtil;
import org.jetbrains.annotations.NotNull;

public final class AppletConfigurationType extends ConfigurationTypeBase {
  AppletConfigurationType() {
    super("Applet", ExecutionBundle.message("applet.configuration.name"), ExecutionBundle.message("applet.configuration.description"), LazyUtil.create(() -> AllIcons.RunConfigurations.Applet));
    addFactory(new ConfigurationFactory(this) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new AppletConfiguration(project, this);
      }

      @Override
      public Class<? extends BaseState> getOptionsClass() {
        return AppletConfigurationOptions.class;
      }
    });
  }

  public static AppletConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AppletConfigurationType.class);
  }
}
