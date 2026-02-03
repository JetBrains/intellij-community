// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class MockGradleRunConfiguration extends ExternalSystemRunConfiguration {
  public MockGradleRunConfiguration(Project project, String name) {
    super(GradleConstants.SYSTEM_ID, project,
          new SimpleConfigurationType(
            "GradleRunConfiguration",
            "Gradle",
            null,
            NotNullLazyValue.lazy(() -> AllIcons.RunConfigurations.Application)) {
            @Override
            public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
              throw new UnsupportedOperationException();
            }
          },
          name);
  }
}
