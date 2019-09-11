// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

interface ExternalSystemProjectNotificationAware : Disposable {
  fun notificationNotify(projectAware: ExternalSystemProjectAware)

  fun notificationExpire(projectId: ExternalSystemProjectId)

  fun notificationExpire()

  companion object {
    fun getInstance(project: Project): ExternalSystemProjectNotificationAware {
      return project.getComponent(ExternalSystemProjectNotificationAware::class.java)
    }
  }
}