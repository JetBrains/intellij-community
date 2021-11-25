// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ExternalSystemProjectNotificationAware {
  fun notificationNotify(projectAware: ExternalSystemProjectAware)
  fun notificationExpire()
  fun notificationExpire(projectId: ExternalSystemProjectId)
  fun hideNotification()
  fun isNotificationVisible(): Boolean
  fun getSystemIds(): Set<ProjectSystemId>

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ExternalSystemProjectNotificationAware = project.getService(ExternalSystemProjectNotificationAware::class.java)
  }
}