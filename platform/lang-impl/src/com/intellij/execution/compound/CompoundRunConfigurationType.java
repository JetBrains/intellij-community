// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.compound;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationSingletonPolicy;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.LazyUtil;
import org.jetbrains.annotations.NotNull;

public final class CompoundRunConfigurationType extends SimpleConfigurationType {
  public CompoundRunConfigurationType() {
    super("CompoundRunConfigurationType",
          "Compound",
          "It runs batch of run configurations at once",
          LazyUtil.create(() -> LayeredIcon.create(AllIcons.Nodes.Folder, AllIcons.Nodes.RunnableMark)));
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new CompoundRunConfiguration(project, "Compound Run Configuration", this);
  }
  
  @NotNull
  @Override
  public RunConfigurationSingletonPolicy getSingletonPolicy() {
    return RunConfigurationSingletonPolicy.SINGLE_INSTANCE_ONLY;
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.CompoundRunConfigurationType";
  }
}
