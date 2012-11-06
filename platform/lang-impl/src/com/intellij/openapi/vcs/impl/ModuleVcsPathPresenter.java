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

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author yole
 */
public class ModuleVcsPathPresenter extends VcsPathPresenter {
  private final Project myProject;

  public ModuleVcsPathPresenter(final Project project) {
    myProject = project;
  }

  @Override
  public String getPresentableRelativePathFor(final VirtualFile file) {
    if (file == null) return "";
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject)
          .getFileIndex();
        Module module = fileIndex.getModuleForFile(file);
        VirtualFile contentRoot = fileIndex.getContentRootForFile(file);
        if (module == null || contentRoot == null) return file.getPresentableUrl();
        StringBuffer result = new StringBuffer();
        result.append("[");
        result.append(module.getName());
        result.append("] ");
        result.append(contentRoot.getName());
        String relativePath = VfsUtilCore.getRelativePath(file, contentRoot, File.separatorChar);
        if (!relativePath.isEmpty()) {
          result.append(File.separatorChar);
          result.append(relativePath);
        }
        return result.toString();
      }
    });
  }

  @Override
  public String getPresentableRelativePath(@NotNull final ContentRevision fromRevision, @NotNull final ContentRevision toRevision) {
    // need to use parent path because the old file is already not there
    FilePath fromPath = fromRevision.getFile();
    FilePath toPath = toRevision.getFile();

    if ((fromPath.getParentPath() == null) || (toPath.getParentPath() == null)) {
      return null;
    }

    final VirtualFile oldFile = fromPath.getParentPath().getVirtualFile();
    final VirtualFile newFile = toPath.getParentPath().getVirtualFile();
    if (oldFile != null && newFile != null) {
      Module oldModule = ModuleUtilCore.findModuleForFile(oldFile, myProject);
      Module newModule = ModuleUtilCore.findModuleForFile(newFile, myProject);
      if (oldModule != newModule) {
        return getPresentableRelativePathFor(oldFile);
      }
    }
    final RelativePathCalculator calculator =
      new RelativePathCalculator(toPath.getIOFile().getAbsolutePath(), fromPath.getIOFile().getAbsolutePath());
    calculator.execute();
    final String result = calculator.getResult();
    return (result == null) ? null : result.replace("/", File.separator);
  }

}
