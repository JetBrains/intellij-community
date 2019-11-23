// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Experimental
interface ExternalSystemProjectTracker : Disposable {

  /**
   * Starts tracking of project settings that will be defined by [projectAware]
   */
  fun register(projectAware: ExternalSystemProjectAware)

  /**
   * Stops tracking of project settings that were defined by [ExternalSystemProjectAware] with [id]
   */
  fun remove(id: ExternalSystemProjectId)

  /**
   * Marks project settings dirty
   * It allows to schedule unconditional project refresh
   */
  fun markDirty(id: ExternalSystemProjectId)

  /**
   * Schedules incremental project refresh
   */
  fun scheduleProjectRefresh()

  /**
   * Schedules update of reload notification status
   */
  fun scheduleProjectNotificationUpdate()

  /**
   * Enables auto-import in tests
   * Note: project tracker automatically enabled out of tests
   */
  @TestOnly
  fun enableAutoImportInTests()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ExternalSystemProjectTracker {
      return ServiceManager.getService(project, ExternalSystemProjectTracker::class.java)
    }
  }
}