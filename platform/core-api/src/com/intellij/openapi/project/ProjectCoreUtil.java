// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ProjectCoreUtil {
  /**
   * @deprecated for internal use only, use {@link com.intellij.psi.PsiElement#getProject()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @ApiStatus.Internal
  public static volatile Project theProject;

  private static volatile Project theOnlyProject;

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
   * @deprecated Please use {@link com.intellij.psi.PsiElement#getProject()} or {@link com.intellij.openapi.project.ProjectManager#getOpenProjects()} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @ApiStatus.Internal
  @Nullable
  public static Project theOnlyOpenProject() {
    // piggyback Disposer.isDebugMode() to convey ApplicationManagerEx.isInStressTest info
    return Disposer.isDebugMode() ? null : theOnlyProject;
  }

  /**
   * Do not use to avoid internal data structures corruption
   */
  @ApiStatus.Internal
  public static void updateInternalTheOnlyProjectFieldTemporarily(Project project) {
    theOnlyProject = project;
  }
}