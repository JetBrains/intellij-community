// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

//todo: use it somewhere where it can hide the files in platform?
/**
 * Provides a way to customize the [com.intellij.openapi.vfs.impl.ReadFilePermission]'s behavior for specific files.
 */
@ApiStatus.Experimental
interface FileVisibilityProvider {
  /**
   * Allows customizing the visibility of a given file within the provided project context.
   *
   * @return a true or false if you want to specify if the file should be visible or not,
   *         or null for the files which visibility you do not intent to affect.
   */
  fun isVisible(project: Project, file: VirtualFile): Boolean?

  companion object {
    @JvmField
    val EP_NAME: ProjectExtensionPointName<FileVisibilityProvider> = ProjectExtensionPointName("com.intellij.fileVisibilityProvider")

    fun isVisible(project: Project, file: VirtualFile, default: Boolean): Boolean {
      var result: Boolean? = null
      EP_NAME.getExtensions(project).forEach {
        val isVisible = it.isVisible(project, file) ?: return@forEach
        if (result == null) result = true

        result = result && isVisible
      }
      return result ?: default
    }
  }
}