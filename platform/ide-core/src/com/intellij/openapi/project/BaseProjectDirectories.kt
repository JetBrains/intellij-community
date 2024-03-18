// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly

/**
 * Provides access to base directories of the project. The default implementation may be changed in your IDE by overriding this service. 
 */
abstract class BaseProjectDirectories(private val project: Project) {
  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<BaseProjectDirectoriesListener> = Topic("Change of base project directories", BaseProjectDirectoriesListener::class.java, Topic.BroadcastDirection.NONE)

    /**
     * Return top-level directories which contain files related to the project. Usually the returned sequence contains a single element, but
     * it may be empty for an empty project, and may contain more than one element if project files are located in different directories.
     * 
     * The default implementation returns all content roots of modules that aren't located under other content roots, but some IDEs may
     * behave differently.
     */
    @JvmStatic
    fun Project.getBaseDirectories(): Set<VirtualFile> = getInstance(this).getBaseDirectories()

    @JvmStatic
    fun getInstance(project: Project): BaseProjectDirectories = project.service<BaseProjectDirectories>()
  }

  /**
   * Returns true when service is processing changes in background
   */
  @get:TestOnly
  abstract val isProcessing : Boolean

  /**
   * Override this function in your implementation of the service to change how base directories are calculated. The implementation must
   * return directories only, and none of the returned elements may be located under another.
   */
  protected abstract fun getBaseDirectories(): Set<VirtualFile>

  /**
   * Returns base directory for specific virtual file
   */
  abstract fun getBaseDirectoryFor(virtualFile: VirtualFile): VirtualFile?

  /**
   * Returns true if this file is located in one of base directories
   */
  fun contains(file: VirtualFile): Boolean = getBaseDirectoryFor(file) != null

  /**
   * Call this method when list of project base directories was changed
   */
  @RequiresEdt
  protected fun fireChange(diff: BaseProjectDirectoriesDiff) {
    project.messageBus.syncPublisher(TOPIC).changed(project, diff)
  }
}
