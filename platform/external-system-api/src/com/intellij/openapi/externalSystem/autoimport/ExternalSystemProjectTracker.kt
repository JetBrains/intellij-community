// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

interface ExternalSystemProjectTracker : Disposable {

  /**
   * Disables project tracking
   * It allows to track project changes manually
   */
  var isDisabled: Boolean

  /**
   * Starts tracking of project settings that will defined by [projectAware]
   * It allows to add some external project updaters on single ide project
   *  e.g. incremental auto update, multiple build systems in single project and etc
   */
  fun register(projectAware: ExternalSystemProjectAware)

  /**
   * Stops tracking of project settings that were defined by [ExternalSystemProjectAware] with [id]
   */
  fun remove(id: ExternalSystemProjectId)

  /**
   * Marks dirty of project settings that will defined by [ExternalSystemProjectAware] with [id]
   * It allows to schedule unconditional project refresh
   *  e.g. broken project model
   */
  fun markDirty(id: ExternalSystemProjectId)

  /**
   * Schedules incremental project refresh
   */
  fun scheduleProjectRefresh()

  /**
   * Schedules update of project notification visibility state
   */
  fun scheduleProjectNotificationUpdate()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ExternalSystemProjectTracker {
      return project.getComponent(ExternalSystemProjectTracker::class.java)
    }
  }
}