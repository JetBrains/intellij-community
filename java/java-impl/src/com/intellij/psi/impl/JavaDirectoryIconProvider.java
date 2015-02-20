/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class JavaDirectoryIconProvider extends IconProvider implements DumbAware {
  @Override
  @Nullable
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof PsiDirectory) {
      final PsiDirectory psiDirectory = (PsiDirectory)element;
      final VirtualFile vFile = psiDirectory.getVirtualFile();
      final Project project = psiDirectory.getProject();

      SourceFolder sourceFolder;
      Icon symbolIcon;
      if (vFile.getParent() == null && vFile.getFileSystem() instanceof ArchiveFileSystem) {
        symbolIcon = PlatformIcons.JAR_ICON;
      }
      else if (ProjectRootsUtil.isModuleContentRoot(vFile, project)) {
        Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vFile);
        symbolIcon = module != null ? ModuleType.get(module).getIcon() : PlatformIcons.CONTENT_ROOT_ICON_CLOSED;
      }
      else if ((sourceFolder = ProjectRootsUtil.getModuleSourceRoot(vFile, project)) != null) {
        symbolIcon = SourceRootPresentation.getSourceRootIcon(sourceFolder);
      }
      else if (JavaDirectoryService.getInstance().getPackage(psiDirectory) != null) {
        symbolIcon = PlatformIcons.PACKAGE_ICON;
      }
      else if (!Registry.is("ide.hide.excluded.files") && ProjectRootManager.getInstance(project).getFileIndex().isExcluded(vFile)) {
        symbolIcon = AllIcons.Modules.ExcludeRoot;
      }
      else {
        symbolIcon = PlatformIcons.DIRECTORY_CLOSED_ICON;
      }

      return ElementBase.createLayeredIcon(element, symbolIcon, 0);
    }

    return null;
  }
}
