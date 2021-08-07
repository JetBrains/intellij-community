// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

public final class UnknownConfigurationType extends SimpleConfigurationType {
  @NotNull
  private static final UnknownConfigurationType INSTANCE = new UnknownConfigurationType();

  private static final String NAME = "Unknown";

  private UnknownConfigurationType() {
    super(NAME, ExecutionBundle.message("run.configuration.unknown.name"), ExecutionBundle.message("run.configuration.unknown.description"),
          NotNullLazyValue.createValue(() -> AllIcons.Actions.Help));
  }

  @NotNull
  public static UnknownConfigurationType getInstance() {
    return INSTANCE;
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new UnknownRunConfiguration(this, project);
  }

  @NotNull
  @Override
  public RunConfigurationSingletonPolicy getSingletonPolicy() {
    // in any case you cannot run UnknownConfigurationType
    return RunConfigurationSingletonPolicy.SINGLE_INSTANCE_ONLY;
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.Unknown";
  }

  @Override
  public boolean isManaged() {
    return false;
  }
}
