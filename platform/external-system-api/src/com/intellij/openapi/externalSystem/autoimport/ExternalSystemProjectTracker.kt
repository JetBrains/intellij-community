// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface ExternalSystemProjectTracker {

  /**
   * Starts tracking of project settings that will be defined by [projectAware]
   *
   * Auto reloads will be activated after first project refresh
   * @see [ExternalSystemProjectTracker.activate] for details
   */
  fun register(projectAware: ExternalSystemProjectAware)

  /**
   * @see [ExternalSystemProjectTracker.register]
   * @param [parentDisposable] allows to remove [projectAware] when it will be disposed
   */
  fun register(projectAware: ExternalSystemProjectAware, parentDisposable: Disposable) {
    register(projectAware)
    Disposer.register(parentDisposable, Disposable { remove(projectAware.projectId) })
  }

  /**
   * Activates auto reload for project with [id]
   *
   * Allows to detect project that loaded from local cashes but previously didn't register here
   */
  fun activate(id: ExternalSystemProjectId)

  /**
   * Stops tracking of project settings that were defined by [ExternalSystemProjectAware] with [id]
   */
  fun remove(id: ExternalSystemProjectId)

  /**
   * Marks project settings as dirty.
   */
  fun markDirty(id: ExternalSystemProjectId)

  /**
   * Marks all external project settings as dirty
   * @see markDirty(ExternalSystemProjectId)
   */
  fun markDirtyAllProjects()

  /**
   * Schedules project reload, may be skipped if project is up-to-date, project is being reloaded or VCS is being updated.
   * Use [markDirtyAllProjects] for force project reload.
   */
  fun scheduleProjectRefresh()

  /**
   * Schedules project reload or notification update.
   * I.e. marks this place as safe to start auto-reload.
   *
   * @see scheduleProjectRefresh
   */
  fun scheduleChangeProcessing()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ExternalSystemProjectTracker {
      return project.getService(ExternalSystemProjectTracker::class.java)
    }
  }
}