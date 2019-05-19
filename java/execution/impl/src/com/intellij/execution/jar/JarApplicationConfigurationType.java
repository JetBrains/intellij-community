// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jar;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

public final class JarApplicationConfigurationType extends SimpleConfigurationType implements ConfigurationType {
  @NotNull
  public static JarApplicationConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(JarApplicationConfigurationType.class);
  }

  public JarApplicationConfigurationType() {
    super("JarApplication", ExecutionBundle.message("jar.application.configuration.name"),
          ExecutionBundle.message("jar.application.configuration.description"),
          NotNullLazyValue.createValue(() -> AllIcons.FileTypes.Archive));
  }

  @Override
  @NotNull
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new JarApplicationConfiguration(project, this, "");
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.JarApplication";
  }
}
