package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class RootConfigurationAccessor {
  @Nullable
  public Library getLibrary(final Library library, final String libraryName, final String libraryLevel) {
    return library;
  }

  @Nullable
  public Sdk getSdk(final Sdk sdk, final String sdkName) {
    return sdk;
  }

  public Module getModule(final Module module, final String moduleName) {
    return module;
  }

  public Sdk getProjectSdk(Project project) {
    return ProjectRootManager.getInstance(project).getProjectJdk();
  }

  public String getProjectSdkName(final Project project) {
    return ProjectRootManager.getInstance(project).getProjectJdkName();
  }
}