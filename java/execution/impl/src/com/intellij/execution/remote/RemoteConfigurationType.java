// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class RemoteConfigurationFactory
 * @author Jeka
 */
package com.intellij.execution.remote;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

public final class RemoteConfigurationType extends SimpleConfigurationType implements DumbAware {
  public RemoteConfigurationType() {
    super("Remote", ExecutionBundle.message("remote.debug.configuration.display.name"), ExecutionBundle.message("remote.debug.configuration.description"),
          NotNullLazyValue.createValue(() -> AllIcons.RunConfigurations.Remote));
  }

  @Override
  public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new RemoteConfiguration(project, this);
  }

  @Override
  public @NotNull String getTag() {
    return "javaRemote";
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.Remote";
  }

  @Deprecated(forRemoval = true)
  public @NotNull ConfigurationFactory getFactory() {
    return this;
  }

  public static @NotNull RemoteConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(RemoteConfigurationType.class);
  }

  @Override
  public boolean isEditableInDumbMode() {
    return true;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
