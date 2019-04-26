// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.scratch;

import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class JavaScratchConfigurationType extends SimpleConfigurationType {
  public JavaScratchConfigurationType() {
    super("Java Scratch", "Java Scratch", "Configuration for java scratch files",
          NotNullLazyValue.createValue(() -> LayeredIcon.create(AllIcons.RunConfigurations.Application, AllIcons.Actions.Scratch)));
  }

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
  public Class<? extends BaseState> getOptionsClass() {
    return JavaScratchConfigurationOptions.class;
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.Java Scratch";
  }

  @NotNull
  public static JavaScratchConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(JavaScratchConfigurationType.class);
  }
}
