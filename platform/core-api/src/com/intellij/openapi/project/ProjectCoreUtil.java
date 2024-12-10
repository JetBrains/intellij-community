// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private static volatile Project theOnlyProject;

  public static boolean isProjectOrWorkspaceFile(@NotNull VirtualFile file) {
    return isProjectOrWorkspaceFile(file, file.getNameSequence());
  }

  public static boolean isProjectOrWorkspaceFile(@NotNull VirtualFile fileOrParent, @NotNull CharSequence fileName) {
    // do not use file.getFileType() to avoid autodetection by content loading for arbitrary files
    FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
    return isProjectOrWorkspaceFile(fileOrParent, fileType);
  }

  public static boolean isProjectOrWorkspaceFile(@NotNull VirtualFile fileOrParent, @Nullable FileType fileType) {
    return fileType instanceof InternalFileType ||
           VfsUtilCore.findContainingDirectory(fileOrParent, Project.DIRECTORY_STORE_FOLDER) != null;
  }

  /**
   * For internal usage only.
   *
   * @deprecated Please use {@link com.intellij.psi.PsiElement#getProject()} or {@link com.intellij.openapi.project.ProjectManager#getOpenProjects()} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @ApiStatus.Internal
  public static @Nullable Project theOnlyOpenProject() {
    return theOnlyProject;
  }

  /**
   * Do not use to avoid internal data structures corruption
   */
  @ApiStatus.Internal
  public static void updateInternalTheOnlyProjectFieldTemporarily(Project project) {
    theOnlyProject = project;
  }
}