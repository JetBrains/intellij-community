// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.ui.ProductIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.SourceRootPresentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class DirectoryPresentationProvider implements ItemPresentationProvider<PsiDirectory> {
  @Override
  public ItemPresentation getPresentation(final @NotNull PsiDirectory directory) {
    final VirtualFile vFile = directory.getVirtualFile();
    final Project project = directory.getProject();
    final String locationString = vFile.getPath();

    if (ProjectRootsUtil.isProjectHome(directory)) {
      final Icon projectIcon = ProductIcons.getInstance().getProjectNodeIcon();
      return new PresentationData(project.getName(), locationString, projectIcon, null);
    }

    if (ProjectRootsUtil.isModuleContentRoot(directory)) {
      final Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vFile);
      assert module != null : directory;
      return new PresentationData(module.getName(), locationString,
                                  PlatformIcons.CONTENT_ROOT_ICON_CLOSED, null);
    }

    if (ProjectRootsUtil.isSourceRoot(directory)) {
      SourceFolder sourceRoot = ProjectRootsUtil.getModuleSourceRoot(vFile, project);
      if (sourceRoot != null) {
        Icon icon = SourceRootPresentation.getSourceRootIcon(sourceRoot);
        return new PresentationData(directory.getName(), locationString, icon, null);
      }
    }

    return new PresentationData(directory.getName(), locationString,
                                PlatformIcons.FOLDER_ICON, null);
  }
}
