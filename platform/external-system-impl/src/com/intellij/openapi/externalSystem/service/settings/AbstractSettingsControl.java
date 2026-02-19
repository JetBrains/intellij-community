// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.settings;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractSettingsControl {

  private @Nullable Project myProject;

  protected AbstractSettingsControl(@Nullable Project project) {
    myProject = project;
  }

  protected AbstractSettingsControl() {
    this(null);
  }

  protected @Nullable Project getProject() {
    return myProject;
  }

  protected void setProject(@Nullable Project project) {
    myProject = project;
  }

  protected void reset(@Nullable WizardContext wizardContext, @Nullable Project project) {
    myProject = wizardContext == null ? project : wizardContext.getProject();
  }
}
