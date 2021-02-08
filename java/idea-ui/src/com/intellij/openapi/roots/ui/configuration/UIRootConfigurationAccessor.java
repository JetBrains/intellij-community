// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.RootConfigurationAccessor;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.workspaceModel.ide.impl.legacyBridge.RootConfigurationAccessorForWorkspaceModel;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class UIRootConfigurationAccessor extends RootConfigurationAccessor implements RootConfigurationAccessorForWorkspaceModel {
  private final Project myProject;
  private final WorkspaceEntityStorageBuilder myActualDiffBuilder;

  public UIRootConfigurationAccessor(@NotNull Project project) {
    this(project, null);
  }

  public UIRootConfigurationAccessor(@NotNull Project project, @Nullable WorkspaceEntityStorageBuilder actualDiffBuilder) {
    myProject = project;
    myActualDiffBuilder = actualDiffBuilder;
  }

  @Override
  public WorkspaceEntityStorageBuilder getActualDiffBuilder() {
    return myActualDiffBuilder;
  }

  @Override
  @Nullable
  public Library getLibrary(Library library, final String libraryName, final String libraryLevel) {
    final StructureConfigurableContext context = ProjectStructureConfigurable.getInstance(myProject).getContext();
    if (library == null) {
      if (libraryName != null) {
        library = context.getLibrary(libraryName, libraryLevel);
      }
    } else {
      final Library model = context.getLibraryModel(library);
      if (model != null) {
        library = model;
      }
      library = context.getLibrary(library.getName(), library.getTable().getTableLevel());
    }
    return library;
  }

  @Override
  @Nullable
  public Sdk getSdk(final Sdk sdk, final String sdkName) {
    final ProjectSdksModel model = ProjectStructureConfigurable.getInstance(myProject).getJdkConfig().getJdksTreeModel();
    return sdkName != null ? model.findSdk(sdkName) : sdk;
  }

  @Override
  public Module getModule(final Module module, final String moduleName) {
    if (module == null) {
      return ProjectStructureConfigurable.getInstance(myProject).getModulesConfig().getModule(moduleName);
    }
    return module;
  }

  @Override
  public Sdk getProjectSdk(@NotNull final Project project) {
    return ProjectStructureConfigurable.getInstance(project).getProjectJdksModel().getProjectSdk();
  }

  @Override
  @Nullable
  public String getProjectSdkName(@NotNull final Project project) {
    final Sdk projectJdk = getProjectSdk(project);
    if (projectJdk != null) {
      return projectJdk.getName();
    }
    final String projectJdkName = ProjectRootManager.getInstance(project).getProjectSdkName();
    final ProjectSdksModel projectJdksModel = ProjectStructureConfigurable.getInstance(project).getProjectJdksModel();
    return projectJdkName != null && projectJdksModel.findSdk(projectJdkName) == null ? projectJdkName : null;
  }
}
