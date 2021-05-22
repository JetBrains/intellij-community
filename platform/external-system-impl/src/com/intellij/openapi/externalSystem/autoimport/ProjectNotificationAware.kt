// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean

class ProjectNotificationAware(private val project: Project) : Disposable {
  private val isHidden = AtomicBoolean(false)
  private val projectsWithNotification = ContainerUtil.newConcurrentSet<ExternalSystemProjectId>()

  fun notificationNotify(projectAware: ExternalSystemProjectAware) {
    val projectId = projectAware.projectId
    LOG.debug("${projectId.readableName}: Notify notification")
    projectsWithNotification.add(projectId)
    revealNotification()
  }

  fun notificationExpire(projectId: ExternalSystemProjectId) {
    LOG.debug("${projectId.readableName}: Expire notification")
    projectsWithNotification.remove(projectId)
    revealNotification()
  }

  fun notificationExpire() {
    LOG.debug("Expire notification")
    projectsWithNotification.clear()
    revealNotification()
  }

  override fun dispose() {
    notificationExpire()
  }

  private fun setHideStatus(isHidden: Boolean) {
    this.isHidden.set(isHidden)
    ProjectRefreshFloatingProvider.updateToolbarComponents(project, this)
  }

  private fun revealNotification() = setHideStatus(false)

  fun hideNotification() = setHideStatus(true)

  fun isNotificationVisible(): Boolean {
    return !isHidden.get() && projectsWithNotification.isNotEmpty()
  }

  fun getSystemIds(): Set<ProjectSystemId> {
    return projectsWithNotification.map { it.systemId }.toSet()
  }

  @TestOnly
  fun getProjectsWithNotification(): Set<ExternalSystemProjectId> {
    return projectsWithNotification.toSet()
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autoimport")

    @JvmStatic
    fun getInstance(project: Project) = project.service<ProjectNotificationAware>()
  }
}