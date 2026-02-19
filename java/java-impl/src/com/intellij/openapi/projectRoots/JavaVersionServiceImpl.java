// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 */
public final class JavaVersionServiceImpl extends JavaVersionService {
  @Override
  public boolean isAtLeast(@NotNull PsiElement element, @NotNull JavaSdkVersion version) {
    return JavaSdkVersionUtil.isAtLeast(element, version);
  }

  @Override
  public @Nullable JavaSdkVersion getJavaSdkVersion(@NotNull PsiElement element) {
    return JavaSdkVersionUtil.getJavaSdkVersion(element);
  }

  @Override
  public boolean isCompilerVersionAtLeast(@NotNull PsiElement element, @NotNull JavaSdkVersion version) {
    if (super.isCompilerVersionAtLeast(element, version)) return true;
    Project project = element.getProject();
    JavaSdkVersion projectVersion = JavaSdkVersionUtil.getJavaSdkVersion(ProjectRootManager.getInstance(project).getProjectSdk());
    if (projectVersion != null && projectVersion.isAtLeast(version)) return true;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      JavaSdkVersion moduleVersion = JavaSdkVersionUtil.getJavaSdkVersion(ModuleRootManager.getInstance(module).getSdk());
      if (moduleVersion != null && moduleVersion.isAtLeast(version)) return true;
    }
    return false;
  }
}
