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
package com.intellij.psi.impl;

import com.intellij.ide.IconProvider;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.IconSet;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
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
  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, final int flags) {
    if (element instanceof PsiDirectory) {
      final PsiDirectory psiDirectory = (PsiDirectory)element;
      final VirtualFile vFile = psiDirectory.getVirtualFile();
      final Project project = psiDirectory.getProject();
      boolean isJarRoot = vFile.getParent() == null && vFile.getFileSystem() instanceof JarFileSystem;
      boolean isContentRoot = ProjectRootsUtil.isModuleContentRoot(vFile, project);
      boolean inTestSource = ProjectRootsUtil.isInTestSource(vFile, project);
      boolean isSourceOrTestRoot = ProjectRootsUtil.isSourceOrTestRoot(vFile, project);
      Icon symbolIcon;
      final boolean isOpen = (flags & Iconable.ICON_FLAG_OPEN) != 0;
      if (isJarRoot) {
        symbolIcon = PlatformIcons.JAR_ICON;
      }
      else if (isContentRoot) {
        Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vFile);
        if (module != null) {
          symbolIcon = module.getModuleType().getNodeIcon(isOpen);
        }
        else {
          symbolIcon = isOpen ? PlatformIcons.CONTENT_ROOT_ICON_OPEN : PlatformIcons.CONTENT_ROOT_ICON_CLOSED;
        }
      }
      else if (isSourceOrTestRoot) {
        symbolIcon = IconSet.getSourceRootIcon(inTestSource, isOpen);
      }
      else if (JavaDirectoryService.getInstance().getPackage(psiDirectory) != null) {
        symbolIcon = isOpen ? PlatformIcons.PACKAGE_OPEN_ICON : PlatformIcons.PACKAGE_ICON;
      }
      else {
        symbolIcon = isOpen ? PlatformIcons.DIRECTORY_OPEN_ICON : PlatformIcons.DIRECTORY_CLOSED_ICON;
      }
      boolean isExcluded = ElementPresentationUtil.isExcluded(vFile, project);
      return ElementBase.createLayeredIcon(symbolIcon, isExcluded ? ElementPresentationUtil.FLAGS_EXCLUDED : 0);
    }
    return null;
  }
}
