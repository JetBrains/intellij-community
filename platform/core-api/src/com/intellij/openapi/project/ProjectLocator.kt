// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.project

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

private val preferredProjects = ThreadLocal.withInitial<MutableMap<VirtualFile, Project>> { HashMap() }

@ApiStatus.NonExtendable
abstract class ProjectLocator {
  companion object {
    // called very often by StubUpdatingIndex
    private var instance: ProjectLocator? = null

    @JvmStatic
    fun getInstance(): ProjectLocator {
      var instance = instance
      if (instance == null) {
        instance = ApplicationManager.getApplication().service<ProjectLocator>()
        ProjectLocator.instance = instance
      }
      return instance
    }

    init {
      ApplicationManager.registerCleaner { instance = null }
    }

    /**
     * Execute `runnable`, making sure that within this computation every call to
     * [guessProjectForFile] for the `file` will return `preferredProject`
     */
    @JvmStatic
    @Internal
    fun withPreferredProject(file: VirtualFile, preferredProject: Project): AccessToken {
      val local = preferredProjects.get()
      val prev = local.put(file, preferredProject)
      return object : AccessToken() {
        override fun finish() {
          if (prev == null) {
            local.remove(file)
          }
          else {
            local.put(file, prev)
          }
        }
      }
    }

    @JvmStatic
    fun getPreferredProject(file: VirtualFile): Project? = preferredProjects.get().get(file)
  }

  /**
   * Returns an open project which contains the given file.
   * This is a guess-method, so if several projects contain the file, only one will be returned.
   * @param file file to be located in projects.
   * @return project which probably contains the file, or null if it couldn't guess (for example, there are no open projects).
   */
  abstract fun guessProjectForFile(file: VirtualFile): Project?

  /**
   * Gets all open projects containing the given file.
   * If none does, an empty list is returned.
   * @param file file to be located in projects.
   * @return list of open projects containing this file.
   */
  abstract fun getProjectsForFile(file: VirtualFile): Collection<Project?>
}
