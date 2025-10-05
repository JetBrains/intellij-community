// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.InternalFileType
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.Volatile

object ProjectCoreUtil {
  @Volatile
  private var theOnlyProject: Project? = null

  /**
   * Guesstimate whether [file] is a part of IntelliJ config:
   * * project, module or workspace file
   * * project store directory
   * * file in the project store directory
   */
  @JvmStatic
  fun isProjectOrWorkspaceFile(file: VirtualFile): Boolean {
    return isProjectOrWorkspaceFile(file, file.nameSequence)
  }

  /**
   * Guesstimate whether [fileOrParent] is a part of IntelliJ config:
   * * project, module or workspace file
   * * project store directory
   * * file in the project store directory
   */
  @ApiStatus.Internal
  fun isProjectOrWorkspaceFile(fileOrParent: VirtualFile, fileName: CharSequence): Boolean {
    // do not use file.getFileType() to avoid autodetection by content loading for arbitrary files
    val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName)
    return isProjectOrWorkspaceFile(fileOrParent, fileType)
  }

  /**
   * Guesstimate whether [fileOrParent] is a part of IntelliJ config:
   * * project, module or workspace file
   * * project store directory
   * * file in the project store directory
   */
  @JvmStatic
  fun isProjectOrWorkspaceFile(fileOrParent: VirtualFile, fileType: FileType?): Boolean {
    return fileType is InternalFileType ||
           VfsUtilCore.findContainingDirectory(fileOrParent, Project.DIRECTORY_STORE_FOLDER) != null
  }

  /**
   * For internal usage only.
   */
  @JvmStatic
  @ApiStatus.ScheduledForRemoval
  @ApiStatus.Internal
  @Deprecated(
    "Please use {@link com.intellij.psi.PsiElement#getProject()} or {@link com.intellij.openapi.project.ProjectManager#getOpenProjects()} instead.")
  fun theOnlyOpenProject(): Project? {
    return theOnlyProject
  }

  /**
   * Do not use to avoid internal data structures corruption
   */
  @ApiStatus.Internal
  fun updateInternalTheOnlyProjectFieldTemporarily(project: Project?) {
    theOnlyProject = project
  }
}