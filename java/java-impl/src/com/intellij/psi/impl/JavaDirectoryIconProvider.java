// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.SourceRootPresentation;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.ui.IconManager;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

final class JavaDirectoryIconProvider extends IconProvider implements DumbAware {
  @Override
  public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
    if (!(element instanceof PsiDirectory psiDirectory)) {
      return null;
    }

    final VirtualFile vFile = psiDirectory.getVirtualFile();
    final Project project = psiDirectory.getProject();

    SourceFolder sourceFolder;
    Icon symbolIcon;
    if (vFile.getParent() == null && vFile.getFileSystem() instanceof ArchiveFileSystem) {
      symbolIcon = PlatformIcons.JAR_ICON;
    }
    else if (ProjectRootsUtil.isModuleContentRoot(vFile, project)) {
      Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vFile);
      symbolIcon = module == null || module.isDisposed() ? PlatformIcons.CONTENT_ROOT_ICON_CLOSED : ModuleType.get(module).getIcon();
    }
    else if (ProjectRootsUtil.findUnloadedModuleByContentRoot(vFile, project) != null) {
      symbolIcon = AllIcons.Modules.UnloadedModule;
    }
    else if ((sourceFolder = ProjectRootsUtil.getModuleSourceRoot(vFile, project)) != null) {
      symbolIcon = SourceRootPresentation.getSourceRootIcon(sourceFolder);
    }
    else if (JrtFileSystem.isModuleRoot(vFile)) {
      symbolIcon = AllIcons.Nodes.Module;
    }
    else if (isValidPackage(psiDirectory)) {
      IconManager iconManager = IconManager.getInstance();
      symbolIcon = iconManager.createLayeredIcon(element, iconManager.tooltipOnlyIfComposite(AllIcons.Nodes.Package), 0);
    }
    else if (!Registry.is("ide.hide.excluded.files") && ProjectRootManager.getInstance(project).getFileIndex().isExcluded(vFile)) {
      symbolIcon = AllIcons.Modules.ExcludeRoot;
    }
    else {
      symbolIcon = PlatformIcons.FOLDER_ICON;
    }
    return IconManager.getInstance().createLayeredIcon(element, symbolIcon, 0);
  }

  /**
   * @return {@code true} if the specified {@code directory} is a package, which name is valid
   */
  private static boolean isValidPackage(@NotNull PsiDirectory directory) {
    Project project = directory.getProject();
    PsiDirectoryFactory factory = project.isDisposed() ? null : PsiDirectoryFactory.getInstance(project);
    return factory != null && factory.isPackage(directory) && factory.isValidPackageName(factory.getQualifiedName(directory, false));
  }
}