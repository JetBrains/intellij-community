// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeView;
import com.intellij.java.library.LibraryWithMavenCoordinatesProperties;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.util.Ref;
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

    var projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiDirectory dir : view.getDirectories()) {
      VirtualFile dirVirtualFile = dir.getVirtualFile();

      if (isInContentRoot(dirVirtualFile, projectFileIndex)) { // cannot distinguish module root from source root
        return ThreeState.YES;
      }

      if (projectFileIndex.isUnderSourceRootOfType(dirVirtualFile, JavaModuleSourceRootTypes.SOURCES)) {
        var module = ModuleUtilCore.findModuleForFile(dirVirtualFile, project);
        if (module == null) return ThreeState.YES;

        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        Sdk sdk = moduleRootManager.getSdk();
        if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
          Ref<Boolean> hasAnyMavenDependencies = new Ref<>(false);

          // make sure it is not false positive inherited Project SDK and look for Maven dependencies
          OrderEnumerator.orderEntries(module).recursively()
            .forEachLibrary(library -> {
              if (library instanceof LibraryEx
                  && ((LibraryEx)library).getProperties() instanceof LibraryWithMavenCoordinatesProperties) {
                hasAnyMavenDependencies.set(true);
                return false;
              }

              return true;
            });

          if (hasAnyMavenDependencies.get()) return ThreeState.NO;
        }
      }
    }

    return ThreeState.UNSURE;
  }
}
