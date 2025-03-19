// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.dashboard.RunDashboardCustomizer;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CommonJavaRunDashboardCustomizer extends RunDashboardCustomizer {
  @Override
  public boolean isApplicable(@NotNull RunnerAndConfigurationSettings settings, @Nullable RunContentDescriptor descriptor) {
    RunConfiguration runConfiguration = settings.getConfiguration();
    return runConfiguration instanceof SingleClassConfiguration ||
           (runConfiguration instanceof CommonJavaRunConfigurationParameters &&
            runConfiguration instanceof ModuleBasedConfiguration &&
            ((ModuleBasedConfiguration<?, ?>)runConfiguration).getConfigurationModule() instanceof JavaRunConfigurationModule);
  }

  @Override
  public @Nullable PsiElement getPsiElement(@NotNull RunDashboardRunConfigurationNode node) {
    return findMainClass(node.getConfigurationSettings().getConfiguration());
  }

  private static @Nullable PsiClass findMainClass(@NotNull RunConfiguration runConfiguration) {
    if (runConfiguration instanceof SingleClassConfiguration) {
      return ((SingleClassConfiguration)runConfiguration).getMainClass();
    }

    if (!(runConfiguration instanceof CommonJavaRunConfigurationParameters) ||
        !(runConfiguration instanceof ModuleBasedConfiguration)) {
      return null;
    }

    RunConfigurationModule configurationModule = ((ModuleBasedConfiguration<?, ?>)runConfiguration).getConfigurationModule();
    if (!(configurationModule instanceof JavaRunConfigurationModule)) return null;

    String runClassName = ((CommonJavaRunConfigurationParameters)runConfiguration).getRunClass();
    if (runClassName == null) return null;

    return ((JavaRunConfigurationModule)configurationModule).findClass(runClassName);
  }
}
