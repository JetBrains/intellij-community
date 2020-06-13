// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
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

  /**
   * Used for automatically assigning an SDK to the project when it gets created.
   * If no SDK is specified in the template project and there is no specific SDK chooser step,
   * the SDK which is set for the project is the highest version SDK for which
   * {@code isSuitableSdk} returns true.
   *
   * @param sdk the candidate SDK
   * @return true if the SDK can be used for this project type, false otherwise
   * @deprecated. Use {@link #isSuitableSdkType(SdkTypeId)} instead.
   */
  @Deprecated
  public boolean isSuitableSdk(Sdk sdk) {
    return isSuitableSdkType(sdk.getSdkType());
  }

  public boolean isSuitableSdkType(SdkTypeId sdkType) {
    return true;
  }

  public @Nullable Project createProject(String name, String path) {
    return ProjectManager.getInstance().createProject(name, path);
  }
}