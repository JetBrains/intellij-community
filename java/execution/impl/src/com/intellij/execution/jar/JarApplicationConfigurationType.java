/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.jar;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class JarApplicationConfigurationType extends ConfigurationTypeBase implements ConfigurationType {
  @NotNull
  public static JarApplicationConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(JarApplicationConfigurationType.class);
  }

  public JarApplicationConfigurationType() {
    super("JarApplication", ExecutionBundle.message("jar.application.configuration.name"),
          ExecutionBundle.message("jar.application.configuration.description"), AllIcons.FileTypes.Archive);
    addFactory(new ConfigurationFactoryEx(this) {
      @Override
      public void onNewConfigurationCreated(@NotNull RunConfiguration configuration) {
        JarApplicationConfiguration jarApplicationConfiguration = (JarApplicationConfiguration)configuration;
        if (StringUtil.isEmpty(jarApplicationConfiguration.getWorkingDirectory())) {
          String baseDir = FileUtil.toSystemIndependentName(StringUtil.notNullize(configuration.getProject().getBasePath()));
          jarApplicationConfiguration.setWorkingDirectory(baseDir);
        }
      }

      @NotNull
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new JarApplicationConfiguration(project, this, "");
      }
    });
  }
}
