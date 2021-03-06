// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ProjectCoreUtil {
  /**
   * @deprecated for internal use only, see {@link #theOnlyOpenProject()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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
   *
   * @return the only open project if there is one, {@code null} if no projects open, or several projects are open, or default project is created
   * @deprecated Please use {@link com.intellij.psi.PsiElement#getProject()} or {@link com.intellij.openapi.project.ProjectManager#getOpenProjects()} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @ApiStatus.Internal
  @Nullable
  public static Project theOnlyOpenProject() {
    return theProject;
  }
}