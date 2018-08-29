// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.scratch;

import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.LazyUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class JavaScratchConfigurationType extends ConfigurationTypeBase {
  public JavaScratchConfigurationType() {
    super("Java Scratch", "Java Scratch", "Configuration for java scratch files", LazyUtil.create(() -> LayeredIcon.create(AllIcons.RunConfigurations.Application, AllIcons.Actions.Scratch)));
    addFactory(new ConfigurationFactoryEx(this) {
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

      @Override
      public Class<? extends BaseState> getOptionsClass() {
        return JavaScratchConfigurationOptions.class;
      }
    });
  }

  /** @noinspection MethodOverridesStaticMethodOfSuperclass*/
  @NotNull
  public static JavaScratchConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(JavaScratchConfigurationType.class);
  }
}
