// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.wizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.projectImport.ProjectImportWizardStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Just a holder for the common useful functionality.
 */
public abstract class AbstractImportFromExternalSystemWizardStep extends ProjectImportWizardStep {

  protected AbstractImportFromExternalSystemWizardStep(@NotNull WizardContext context) {
    super(context);
  }

  @Override
  protected @Nullable AbstractExternalProjectImportBuilder getBuilder() {
    return (AbstractExternalProjectImportBuilder)getWizardContext().getProjectBuilder();
  }
}
