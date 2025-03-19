// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.scratch;

import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.scratch.ScratchFileTypeIcon;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class JavaScratchConfigurationType extends SimpleConfigurationType {
  public JavaScratchConfigurationType() {
    super("Java Scratch", JavaCompilerBundle.message("java.scratch"), JavaCompilerBundle.message("configuration.for.java.scratch.files"),
          NotNullLazyValue.createValue(() -> new ScratchFileTypeIcon(AllIcons.RunConfigurations.Application)));
  }

  @Override
  public boolean isApplicable(@NotNull Project project) {
    return false;
  }

  @Override
  public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
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

  public static @NotNull JavaScratchConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(JavaScratchConfigurationType.class);
  }
}
