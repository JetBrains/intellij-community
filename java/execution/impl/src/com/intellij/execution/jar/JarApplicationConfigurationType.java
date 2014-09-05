/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

      public RunConfiguration createTemplateConfiguration(Project project) {
        return new JarApplicationConfiguration(project, this, "");
      }
    });
  }
}
