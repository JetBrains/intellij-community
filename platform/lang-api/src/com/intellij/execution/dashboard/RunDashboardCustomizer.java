// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Register this extension to customize Run Dashboard.
 */
public abstract class RunDashboardCustomizer {
  public static final ExtensionPointName<RunDashboardCustomizer> CUSTOMIZER_EP_NAME =
    new ExtensionPointName<>("com.intellij.runDashboardCustomizer");

  /**
   * @deprecated Use
   */
  @Deprecated(forRemoval = true)
  public static final Key<Map<Object, Object>> NODE_LINKS = new Key<>("RunDashboardNodeLink");

  public abstract boolean isApplicable(@NotNull RunnerAndConfigurationSettings settings, @Nullable RunContentDescriptor descriptor);

  @Deprecated(forRemoval = true)
  public boolean updatePresentation(@NotNull PresentationData presentation, @NotNull RunDashboardRunConfigurationNode node) {
    return false;
  }

  public boolean updatePresentation(@NotNull RunDashboardCustomizationBuilder customizationBuilder, @NotNull RunnerAndConfigurationSettings settings, @Nullable RunContentDescriptor descriptor) {
    return false;
  }

  // TODO: extract into separate backend extension point
  public @Nullable PsiElement getPsiElement(@NotNull RunConfiguration configuration) {
    return null;
  }
}
