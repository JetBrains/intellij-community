// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

/**
 * Bridge between auto-reload backend and notification view about that project is needed to reload.
 * Notifications can be shown in editor floating toolbar, editor banner, etc.
 */
interface ExternalSystemProjectNotificationAware {

  /**
   * Requests to show notifications for reload project that defined by [projectAware]
   */
  fun notificationNotify(projectAware: ExternalSystemProjectAware)

  /**
   * Requests to hide all notifications for all projects.
   */
  fun notificationExpire()

  /**
   * Requests to hide all notifications for project that defined by [projectId]
   * @see ExternalSystemProjectAware.projectId
   */
  fun notificationExpire(projectId: ExternalSystemProjectId)

  /**
   * Checks that notifications should be shown.
   */
  fun isNotificationVisible(): Boolean

  /**
   * Gets list of project ids which should be reloaded.
   */
  fun getSystemIds(): Set<ProjectSystemId>

  interface Listener {

    /**
     * Happens when notification should be shown or hidden.
     */
    @JvmDefault
    fun onNotificationChanged(project: Project) {
    }
  }

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC = Topic.create("ExternalSystemProjectNotificationAware", Listener::class.java)

    @JvmStatic
    fun getInstance(project: Project): ExternalSystemProjectNotificationAware =
      project.getService(ExternalSystemProjectNotificationAware::class.java)

    /**
     * Function for simple subscription onto notification change events
     * @see ExternalSystemProjectNotificationAware.Listener.onNotificationChanged
     */
    fun whenNotificationChanged(project: Project, listener: () -> Unit) = whenNotificationChanged(project, listener, null)

    /**
     * Function for simple subscription onto notification change events
     * @see ExternalSystemProjectNotificationAware.Listener.onNotificationChanged
     */
    fun whenNotificationChanged(project: Project, listener: () -> Unit, parentDisposable: Disposable? = null) {
      val aProject = project
      val messageBus = ApplicationManager.getApplication().messageBus
      val connection = messageBus.connect(parentDisposable ?: project)
      connection.subscribe(TOPIC, object : Listener {
        override fun onNotificationChanged(project: Project) {
          if (aProject === project) {
            listener()
          }
        }
      })
    }
  }
}