/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.project;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ProjectCoreUtil {
  @Deprecated
  @ApiStatus.Internal
  public static volatile Project theProject;

  public static boolean isProjectOrWorkspaceFile(@NotNull VirtualFile file) {
    // do not use file.getFileType() to avoid autodetection by content loading for arbitrary files
    return isProjectOrWorkspaceFile(file, FileTypeRegistry.getInstance().getFileTypeByFileName(file.getNameSequence()));
  }

  public static boolean isProjectOrWorkspaceFile(@NotNull VirtualFile file, @Nullable FileType fileType) {
    return fileType instanceof InternalFileType ||
           VfsUtilCore.findContainingDirectory(file, Project.DIRECTORY_STORE_FOLDER) != null;
  }

  /**
   * For internal usage only.
   * Please use {@link com.intellij.psi.PsiElement#getProject()} or {@link com.intellij.openapi.project.ProjectManager#getOpenProjects()} instead.
   * @return the only open project if there is one, null if no projects open, or several projects are open, or default project is created
   */
  @Deprecated
  @ApiStatus.Internal
  @Nullable
  public static Project theOnlyOpenProject() {
    return theProject;
  }
}