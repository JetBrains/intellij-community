// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Provides access to base directories of the project. The default implementation may be changed in your IDE by overriding this service. 
 */
abstract class BaseProjectDirectories {
  companion object {
    /**
     * Return top-level directories which contain files related to the project. Usually the returned sequence contains a single element, but
     * it may be empty for an empty project, and may contain more than one element if project files are located in different directories.
     * 
     * The default implementation returns all content roots of modules which aren't located under other content roots, but some IDEs may
     * behave differently.
     * 
     * The function must be called under Read Action, and the returned sequence must be processed under the same Read Action.
     */
    @RequiresReadLock
    @JvmStatic
    fun Project.getBaseDirectories(): Sequence<VirtualFile> = getInstance(this).getBaseDirectories()

    @JvmStatic
    fun getInstance(project: Project): BaseProjectDirectories = project.service<BaseProjectDirectories>()
  }

  /**
   * Override this function in your implementation of the service to change how base directories are calculated. The implementation must
   * return directories only, and none of the returned elements may be located under another.
   */
  protected abstract fun getBaseDirectories(): Sequence<VirtualFile>

  /**
   * Returns base directory for specific virtual file
   */
  abstract fun getBaseDirectoryFor(virtualFile: VirtualFile): VirtualFile?

  /**
   * Returns true if this file is located in one of base directories
   */
  fun contains(file: VirtualFile) = getBaseDirectoryFor(file) != null
}
