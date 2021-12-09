// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly

@Suppress("TestOnlyProblems")
@Deprecated("This API was replaced", ReplaceWith("ExternalSystemProjectNotificationAware", "com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware"))
class ProjectNotificationAware(private val project: Project) : ExternalSystemProjectNotificationAware, Disposable {
  override fun notificationNotify(projectAware: ExternalSystemProjectAware) {
    AutoImportProjectNotificationAware.getInstance(project).notificationNotify(projectAware)
  }

  override fun notificationExpire(projectId: ExternalSystemProjectId) {
    AutoImportProjectNotificationAware.getInstance(project).notificationExpire(projectId)
  }

  override fun notificationExpire() {
    AutoImportProjectNotificationAware.getInstance(project).notificationExpire()
  }

  @Suppress("SSBasedInspection")
  override fun dispose() {
    AutoImportProjectNotificationAware.getInstance(project).dispose()
  }

  override fun getSystemIds(): Set<ProjectSystemId> {
    return AutoImportProjectNotificationAware.getInstance(project).getSystemIds()
  }

  @TestOnly
  fun getProjectsWithNotification(): Set<ExternalSystemProjectId> {
    return AutoImportProjectNotificationAware.getInstance(project).getProjectsWithNotification()
  }

  override fun isNotificationVisible(): Boolean {
    return AutoImportProjectNotificationAware.getInstance(project).isNotificationVisible()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectNotificationAware = project.getService(ProjectNotificationAware::class.java)
  }
}