// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ProjectBuilder {
  public boolean isUpdate() {
    return false;
  }

  public abstract @Nullable List<Module> commit(@NotNull Project project, @Nullable ModifiableModuleModel model, ModulesProvider modulesProvider);

  public List<Module> commit(@NotNull Project project) {
    return commit(project, null, DefaultModulesProvider.createForProject(project));
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
