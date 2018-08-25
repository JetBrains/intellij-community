// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.scratch;

import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public class JavaScratchConfigurationType extends ApplicationConfigurationType{
  private final ConfigurationFactory myFactory;

  public JavaScratchConfigurationType() {
    myFactory = new ConfigurationFactoryEx(this) {
      @Override
      public boolean isApplicable(@NotNull Project project) {
        return false;
      }

      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new JavaScratchConfiguration("", project, this);
      }

      @Override
      public void onNewConfigurationCreated(@NotNull RunConfiguration configuration) {
        ((ModuleBasedConfiguration)configuration).onNewConfigurationCreated();
      }
    };
  }

  @NotNull
  @Override
  public String getId() {
    return "Java Scratch";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Java Scratch";
  }

  @Override
  public String getConfigurationTypeDescription() {
    return "Configuration for java scratch files";
  }

  @Override
  public Icon getIcon() {
    return LayeredIcon.create(super.getIcon(), AllIcons.Actions.Scratch); // todo
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {myFactory};
  }

  /** @noinspection MethodOverridesStaticMethodOfSuperclass*/
  @NotNull
  public static JavaScratchConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(JavaScratchConfigurationType.class);
  }
}
