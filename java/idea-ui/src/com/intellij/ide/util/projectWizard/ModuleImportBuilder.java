// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

final class ModuleImportBuilder extends ProjectImportBuilder {
  @Override
  public @NotNull String getName() {
    return JavaUiBundle.message("add.idea.module.label");
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public boolean isMarked(Object element) {
    return false;
  }

  @Override
  public void setOpenProjectSettingsAfter(boolean on) {
  }

  @Override
  public @Nullable List<Module> commit(Project project,
                                       ModifiableModuleModel model,
                                       ModulesProvider modulesProvider,
                                       ModifiableArtifactModel artifactModel) {

    return ExistingModuleLoader.setUpLoader(getFileToImport()).commit(project, model, modulesProvider);
  }
}
