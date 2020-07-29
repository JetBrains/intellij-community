// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

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
  @NotNull
  @Override
  public String getName() {
    return "Add IDEA module";
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

  @Nullable
  @Override
  public List<Module> commit(Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel) {

    return ExistingModuleLoader.setUpLoader(getFileToImport()).commit(project, model, modulesProvider);
  }
}
