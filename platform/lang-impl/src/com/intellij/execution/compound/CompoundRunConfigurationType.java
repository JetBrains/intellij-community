/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.compound;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;

public class CompoundRunConfigurationType extends ConfigurationTypeBase {

  public static CompoundRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(CompoundRunConfigurationType.class);
  }

  public CompoundRunConfigurationType() {
    super("CompoundRunConfigurationType",
          "Compound",
          "It runs batch of run configurations at once",
          LayeredIcon.create(AllIcons.Nodes.Folder, AllIcons.Nodes.RunnableMark));
    addFactory(new ConfigurationFactory(this) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new CompoundRunConfiguration(project, CompoundRunConfigurationType.this, "Compound Run Configuration");
      }

      @Override
      public String getName() {
        return "Compound Run Configuration";
      }

      @Override
      public boolean isConfigurationSingletonByDefault() {
        return true;
      }

      @Override
      public boolean canConfigurationBeSingleton() {
        return false;
      }
    });
  }


}
