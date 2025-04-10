// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class AutoImportProjectNotificationAware(private val project: Project) : ExternalSystemProjectNotificationAware, Disposable {
  private val projectsWithNotification = ConcurrentCollectionFactory.createConcurrentSet<ExternalSystemProjectId>()

  override fun notificationNotify(projectAware: ExternalSystemProjectAware) {
    val projectId = projectAware.projectId
    LOG.debug("$projectId: Notify notification")
    projectsWithNotification.add(projectId)
    fireNotificationUpdated()
  }

  override fun notificationExpire(projectId: ExternalSystemProjectId) {
    LOG.debug("$projectId: Expire notification")
    projectsWithNotification.remove(projectId)
    fireNotificationUpdated()
  }

  override fun notificationExpire() {
    LOG.debug("Expire notification")
    projectsWithNotification.clear()
    fireNotificationUpdated()
  }

  override fun dispose() {
    notificationExpire()
  }

  private fun fireNotificationUpdated() {
    ApplicationManager.getApplication().messageBus
      .syncPublisher(ExternalSystemProjectNotificationAware.TOPIC)
      .onNotificationChanged(project)
  }

  override fun isNotificationVisible(): Boolean {
    return projectsWithNotification.isNotEmpty()
  }

  override fun isNotificationVisible(systemId: ProjectSystemId): Boolean {
    return projectsWithNotification.any { it.systemId == systemId }
  }

  override fun getSystemIds(): Set<ProjectSystemId> {
    return projectsWithNotification.map { it.systemId }.toSet()
  }

  @TestOnly
  fun getProjectsWithNotification(): Set<ExternalSystemProjectId> {
    return projectsWithNotification.toSet()
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autoimport")

    @TestOnly
    @JvmStatic
    fun getInstance(project: Project): AutoImportProjectNotificationAware {
      return ExternalSystemProjectNotificationAware.getInstance(project) as AutoImportProjectNotificationAware
    }
  }
}