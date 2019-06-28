// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.dashboard.RunDashboardCustomizer;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.util.PsiNavigateUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommonJavaRunDashboardCustomizer extends RunDashboardCustomizer {
  @Override
  public boolean isApplicable(@NotNull RunnerAndConfigurationSettings settings, @Nullable RunContentDescriptor descriptor) {
    RunConfiguration runConfiguration = settings.getConfiguration();
    return runConfiguration instanceof SingleClassConfiguration ||
           (runConfiguration instanceof CommonJavaRunConfigurationParameters &&
            runConfiguration instanceof ModuleBasedConfiguration &&
            ((ModuleBasedConfiguration)runConfiguration).getConfigurationModule() instanceof JavaRunConfigurationModule);
  }

  @Override
  @Nullable
  public Navigatable getNavigatable(@NotNull RunDashboardRunConfigurationNode node) {
    PsiClass mainClass = findMainClass(node.getConfigurationSettings().getConfiguration());
    if (mainClass == null || !mainClass.isValid()) return null;

    return new Navigatable() {
      @Override
      public void navigate(boolean requestFocus) {
        PsiNavigateUtil.navigate(mainClass, requestFocus);
      }

      @Override
      public boolean canNavigate() {
        return true;
      }

      @Override
      public boolean canNavigateToSource() {
        return true;
      }
    };
  }

  @Nullable
  private static PsiClass findMainClass(@NotNull RunConfiguration runConfiguration) {
    if (runConfiguration instanceof SingleClassConfiguration) {
      return ((SingleClassConfiguration)runConfiguration).getMainClass();
    }

    if (!(runConfiguration instanceof CommonJavaRunConfigurationParameters) ||
        !(runConfiguration instanceof ModuleBasedConfiguration)) {
      return null;
    }

    RunConfigurationModule configurationModule = ((ModuleBasedConfiguration)runConfiguration).getConfigurationModule();
    if (!(configurationModule instanceof JavaRunConfigurationModule)) return null;

    String runClassName = ((CommonJavaRunConfigurationParameters)runConfiguration).getRunClass();
    if (runClassName == null) return null;

    return ((JavaRunConfigurationModule)configurationModule).findClass(runClassName);
  }
}
