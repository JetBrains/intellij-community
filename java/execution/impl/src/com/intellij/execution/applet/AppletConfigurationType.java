// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.applet;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

public final class AppletConfigurationType extends SimpleConfigurationType {
  AppletConfigurationType() {
    super("Applet", ExecutionBundle.message("applet.configuration.name"), ExecutionBundle.message("applet.configuration.description"),
          NotNullLazyValue.createValue(() -> AllIcons.RunConfigurations.Applet));
  }

  @Override
  public Class<? extends BaseState> getOptionsClass() {
    return AppletConfigurationOptions.class;
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new AppletConfiguration(project, this);
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.Applet";
  }
}
