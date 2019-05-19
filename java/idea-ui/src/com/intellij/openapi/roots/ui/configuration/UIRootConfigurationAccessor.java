/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.RootConfigurationAccessor;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class UIRootConfigurationAccessor extends RootConfigurationAccessor {
  private final Project myProject;

  public UIRootConfigurationAccessor(final Project project) {
    myProject = project;
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
      return ModuleStructureConfigurable.getInstance(myProject).getModule(moduleName);
    }
    return module;
  }

  @Override
  public Sdk getProjectSdk(final Project project) {
    return ProjectStructureConfigurable.getInstance(project).getProjectJdksModel().getProjectSdk();
  }

  @Override
  @Nullable
  public String getProjectSdkName(final Project project) {
    final Sdk projectJdk = getProjectSdk(project);
    if (projectJdk != null) {
      return projectJdk.getName();
    }
    final String projectJdkName = ProjectRootManager.getInstance(project).getProjectSdkName();
    final ProjectSdksModel projectJdksModel = ProjectStructureConfigurable.getInstance(project).getProjectJdksModel();
    return projectJdkName != null && projectJdksModel.findSdk(projectJdkName) == null ? projectJdkName : null;
  }
}
