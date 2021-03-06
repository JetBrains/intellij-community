// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectConfigurator;
import org.jetbrains.annotations.NotNull;

public class NoContentRootProjectConfigurator implements DirectoryProjectConfigurator {
  @Override
  public void configureProject(@NotNull Project project,
                               @NotNull final VirtualFile baseDir,
                               @NotNull final Ref<Module> moduleRef,
                               boolean isProjectCreatedWithWizard) {
    VirtualFile projectsRoot = LocalFileSystem.getInstance().findFileByPath(ProjectUtil.getProjectsPath());
    if (projectsRoot == null || !VfsUtilCore.isAncestor(projectsRoot, baseDir, true)) return;

    ApplicationManager.getApplication().runWriteAction(() -> {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        ModifiableRootModel rootModel = rootManager.getModifiableModel();
        for (ContentEntry entry : rootModel.getContentEntries()) {
          VirtualFile entryFile = entry.getFile();
          if (entryFile != null && VfsUtilCore.isAncestor(projectsRoot, entryFile, true)) {
            rootModel.removeContentEntry(entry);
          }
        }
        rootModel.inheritSdk();
        rootModel.commit();
      }
    });
  }
}
