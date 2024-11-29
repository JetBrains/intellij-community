// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import static com.intellij.ide.actions.CreateTemplateInPackageAction.JAVA_NEW_FILE_CATEGORY;
import static com.intellij.ide.actions.CreateTemplateInPackageAction.isInContentRoot;

final class JavaNewFileCategoryHandler implements NewFileActionCategoryHandler {
  @Override
  public @NotNull ThreeState isVisible(@NotNull DataContext dataContext, @NotNull String category) {
    if (JAVA_NEW_FILE_CATEGORY.equals(category)) return ThreeState.YES;
    if (AdvancedSettings.getBoolean("java.show.irrelevant.templates.in.source.roots")) return ThreeState.UNSURE;

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);

    if (project == null || view == null) return ThreeState.UNSURE;

    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiDirectory dir : view.getDirectories()) {
      VirtualFile dirVirtualFile = dir.getVirtualFile();

      if (isInContentRoot(dirVirtualFile, projectFileIndex)) { // cannot distinguish module root from source root
        return ThreeState.YES;
      }
      if (projectFileIndex.isUnderSourceRootOfType(dirVirtualFile, JavaModuleSourceRootTypes.SOURCES)) {
        var module = ModuleUtilCore.findModuleForFile(dirVirtualFile, project);
        if (module == null) return ThreeState.YES;

        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
          return ThreeState.NO;
        }
      }
    }

    return ThreeState.UNSURE;
  }
}
