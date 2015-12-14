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
package com.intellij.navigation;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.SourceRootPresentation;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.PlatformIcons;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DirectoryPresentationProvider implements ItemPresentationProvider<PsiDirectory> {
  @Override
  public ItemPresentation getPresentation(@NotNull final PsiDirectory directory) {
    final VirtualFile vFile = directory.getVirtualFile();
    final Project project = directory.getProject();
    final String locationString = vFile.getPath();

    if (ProjectRootsUtil.isProjectHome(directory)) {
      final Icon projectIcon = PlatformUtils.isJetBrainsProduct()
                               ? AllIcons.Nodes.IdeaProject
                               : IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getSmallIconUrl());
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
                                PlatformIcons.DIRECTORY_CLOSED_ICON, null);
  }
}
