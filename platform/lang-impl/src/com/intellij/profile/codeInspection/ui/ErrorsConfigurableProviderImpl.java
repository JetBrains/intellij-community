// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;

public final class ErrorsConfigurableProviderImpl extends ErrorsConfigurableProvider {
  private final Project project;

  public ErrorsConfigurableProviderImpl(Project project) {
    this.project = project;
  }

  @Override
  public ErrorsConfigurable createConfigurable() {
    return new ProjectInspectionToolsConfigurable(ProjectInspectionProfileManager.getInstance(project));
  }
}
