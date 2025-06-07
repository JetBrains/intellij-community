// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path

/**
 * An application service for interacting with project configuration store without an instance of a [Project]
 * In all but a limited number of cases you should use [Project.componentStore]
 */
@Internal
interface ProjectStorePathManager {
  /**
   * Build a path to the project store directory
   *
   * @param projectRoot root directory of the project
   */
  fun getStoreDirectoryPath(projectRoot: Path): Path

  /**
   * Query the filesystem for an existing project store directory
   *
   * @param projectRoot root directory of the project
   */
  @RequiresBackgroundThread
  fun testStoreDirectoryExistsForProjectRoot(projectRoot: Path): Boolean {
    return Files.isDirectory(getStoreDirectoryPath(projectRoot))
  }

  /**
   * File a file representing a project store directory
   *
   * @param projectRoot root directory of the project
   */
  fun getStoreDirectory(projectRoot: VirtualFile): VirtualFile? {
    return if (projectRoot.isDirectory) {
      val fileSystem = projectRoot.fileSystem
      val rootPath = fileSystem.getNioPath(projectRoot) ?: return null
      fileSystem.findFileByPath(getStoreDirectoryPath(rootPath).toString())?.takeIf { it.isDirectory }
    }
    else {
      null
    }
  }

  /**
   * Query the VFS for an existing project store directory
   *
   * @param projectRoot root directory of the project
   */
  fun testStoreDirectoryExistsForProjectRoot(projectRoot: VirtualFile): Boolean {
    return getStoreDirectory(projectRoot) != null
  }

  companion object {
    @JvmStatic
    fun getInstance(): ProjectStorePathManager = ApplicationManager.getApplication().getService(ProjectStorePathManager::class.java)
  }
}