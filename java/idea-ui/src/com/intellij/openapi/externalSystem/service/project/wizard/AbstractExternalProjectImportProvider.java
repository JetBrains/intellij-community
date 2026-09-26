// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.wizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Provides 'import from external model' functionality.
 */
public abstract class AbstractExternalProjectImportProvider extends ProjectImportProvider {
  private final @NotNull ProjectSystemId myExternalSystemId;

  public AbstractExternalProjectImportProvider(ProjectImportBuilder builder, @NotNull ProjectSystemId externalSystemId) {
    super(builder);
    myExternalSystemId = externalSystemId;
  }

  public AbstractExternalProjectImportProvider(@NotNull ProjectSystemId externalSystemId) {
    myExternalSystemId = externalSystemId;
  }

  public @NotNull ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  @Override
  public ModuleWizardStep[] createSteps(WizardContext context) {
    return new ModuleWizardStep[] { new SelectExternalProjectStep(context) };
  }

  @Override
  public String getPathToBeImported(VirtualFile file) {
    return file.getPath();
  }
}
