// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.settings;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractSettingsControl {

  private @Nullable Project project;

  AbstractSettingsControl(@Nullable Project project) {
    this.project = project;
  }

  AbstractSettingsControl() {
    this(null);
  }

  protected void setProject(@Nullable Project project) {
    this.project = project;
  }

  @Nullable
  protected Project getProject() {
    return project;
  }

  void reset(@Nullable WizardContext wizardContext) {
    project = wizardContext == null ? null : wizardContext.getProject();
  }
}
