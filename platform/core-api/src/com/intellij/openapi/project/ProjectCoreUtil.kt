// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.InternalFileType
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.Volatile

object ProjectCoreUtil {
  @Volatile
  private var theOnlyProject: Project? = null

  @JvmStatic
  fun isProjectOrWorkspaceFile(file: VirtualFile): Boolean {
    return isProjectOrWorkspaceFile(file, file.nameSequence)
  }

  @JvmStatic
  fun isProjectOrWorkspaceFile(fileOrParent: VirtualFile, fileName: CharSequence): Boolean {
    // do not use file.getFileType() to avoid autodetection by content loading for arbitrary files
    val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName)
    return isProjectOrWorkspaceFile(fileOrParent, fileType)
  }

  @JvmStatic
  fun isProjectOrWorkspaceFile(fileOrParent: VirtualFile, fileType: FileType?): Boolean {
    return fileType is InternalFileType ||
           VfsUtilCore.findContainingDirectory(fileOrParent, Project.DIRECTORY_STORE_FOLDER) != null
  }

  @Internal
  @RequiresBackgroundThread
  @JvmStatic
  fun isKnownProjectDirectory(projectRoot: Path): Boolean {
    return Files.isDirectory(projectRoot.resolve(Project.DIRECTORY_STORE_FOLDER))
  }

  @Internal
  @JvmStatic
  fun getProjectStoreDirectory(projectRoot: Path): Path {
    return projectRoot.resolve(Project.DIRECTORY_STORE_FOLDER)
  }

  @Internal
  @JvmStatic
  fun isKnownProjectDirectory(projectRoot: VirtualFile): Boolean {
    return getProjectStoreDirectory(projectRoot) != null
  }

  @Internal
  @JvmStatic
  fun getProjectStoreDirectory(projectRoot: VirtualFile): VirtualFile? {
    return if (projectRoot.isDirectory) projectRoot.findChild(Project.DIRECTORY_STORE_FOLDER) else null
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