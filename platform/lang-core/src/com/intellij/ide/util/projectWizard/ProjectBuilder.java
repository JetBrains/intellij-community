// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ProjectBuilder {
  public boolean isUpdate() {
    return false;
  }

  public abstract @Nullable List<Module> commit(@NotNull Project project, @Nullable ModifiableModuleModel model, ModulesProvider modulesProvider);

  public @Nullable List<Module> commit(@NotNull Project project, @Nullable ModifiableModuleModel model) {
    return commit(project, model, DefaultModulesProvider.createForProject(project));
  }

  public @Nullable List<Module> commit(@NotNull Project project) {
    return commit(project, null);
  }

  public boolean validate(@Nullable Project currentProject, @NotNull Project project) {
    return true;
  }

  public void cleanup() {}

  public boolean isOpenProjectSettingsAfter() {
    return false;
  }

  public boolean isSuitableSdkType(SdkTypeId sdkType) {
    return true;
  }

  public @Nullable Project createProject(String name, String path) {
    return ProjectManager.getInstance().createProject(name, path);
  }
}
