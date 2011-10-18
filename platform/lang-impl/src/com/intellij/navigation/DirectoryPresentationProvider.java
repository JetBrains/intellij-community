/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.PlatformIcons;

public class DirectoryPresentationProvider implements ItemPresentationProvider<PsiDirectory> {
  @Override
  public ItemPresentation getPresentation(final PsiDirectory directory) {
    final VirtualFile vFile = directory.getVirtualFile();
    final String locationString = vFile.getPath();

    final Project project = directory.getProject();
    if (vFile.equals(project.getBaseDir())) {
      return new PresentationData(project.getName(), locationString,
                                  PlatformIcons.PROJECT_ICON, PlatformIcons.PROJECT_ICON, null);
    }

    if (ProjectRootsUtil.isModuleContentRoot(directory)) {
      final Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vFile);
      assert module != null : directory;
      return new PresentationData(module.getName(), locationString,
                                  PlatformIcons.CONTENT_ROOT_ICON_OPEN, PlatformIcons.CONTENT_ROOT_ICON_CLOSED, null);
    }

    if (ProjectRootsUtil.isSourceRoot(directory)) {
      if (ProjectRootsUtil.isInTestSource(directory)) {
        return new PresentationData(directory.getName(), locationString,
                                    PlatformIcons.MODULES_TEST_SOURCE_FOLDER, PlatformIcons.MODULES_TEST_SOURCE_FOLDER, null);
      }
      else {
        return new PresentationData(directory.getName(), locationString,
                                    PlatformIcons.MODULES_SOURCE_FOLDERS_ICON, PlatformIcons.MODULES_SOURCE_FOLDERS_ICON, null);
      }
    }

    return new PresentationData(directory.getName(), locationString,
                                PlatformIcons.DIRECTORY_OPEN_ICON, PlatformIcons.DIRECTORY_CLOSED_ICON, null);
  }
}
