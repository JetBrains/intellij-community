// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Experimental
class AutoImportProjectNotificationAware(private val project: Project) : ExternalSystemProjectNotificationAware, Disposable {
  private val isHidden = AtomicBoolean(false)
  private val projectsWithNotification = ContainerUtil.newConcurrentSet<ExternalSystemProjectId>()

  override fun notificationNotify(projectAware: ExternalSystemProjectAware) {
    val projectId = projectAware.projectId
    LOG.debug("${projectId.debugName}: Notify notification")
    projectsWithNotification.add(projectId)
    revealNotification()
  }

  override fun notificationExpire(projectId: ExternalSystemProjectId) {
    LOG.debug("${projectId.debugName}: Expire notification")
    projectsWithNotification.remove(projectId)
    revealNotification()
  }

  override fun notificationExpire() {
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

  override fun hideNotification() = setHideStatus(true)

  override fun isNotificationVisible(): Boolean {
    return !isHidden.get() && projectsWithNotification.isNotEmpty()
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