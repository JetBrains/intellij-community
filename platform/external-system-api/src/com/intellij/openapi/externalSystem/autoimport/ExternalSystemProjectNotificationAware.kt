// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * Bridge between auto-reload backend and notification view about that project is needed to reload.
 * Notifications can be shown in editor floating toolbar, editor banner, etc.
 */
@ApiStatus.NonExtendable
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
    fun onNotificationChanged(project: Project) {
    }
  }

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<Listener> = Topic.create("ExternalSystemProjectNotificationAware", Listener::class.java)

    @JvmStatic
    fun getInstance(project: Project): ExternalSystemProjectNotificationAware =
      project.getService(ExternalSystemProjectNotificationAware::class.java)

    fun isNotificationVisible(project: Project): Boolean {
      return getInstance(project).getSystemIds().isNotEmpty()
    }

    fun isNotificationVisible(project: Project, systemId: ProjectSystemId): Boolean {
      return systemId in getInstance(project).getSystemIds()
    }

    /**
     * Function for simple subscription onto notification change events
     * @see ExternalSystemProjectNotificationAware.Listener.onNotificationChanged
     */
    fun whenNotificationChanged(project: Project, listener: () -> Unit): Unit = whenNotificationChanged(project, null, listener)
    fun whenNotificationChanged(project: Project, parentDisposable: Disposable?, listener: () -> Unit) {
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

    @Deprecated("Use ExternalSystemProjectNotificationAware.isNotificationVisible instead")
    fun isNotificationVisibleProperty(project: Project, systemId: ProjectSystemId): ObservableProperty<Boolean> {
      return object : ObservableProperty<Boolean> {
        override fun get() = isNotificationVisible(project, systemId)
        override fun afterChange(parentDisposable: Disposable?, listener: (Boolean) -> Unit) {
          whenNotificationChanged(project, parentDisposable) {
            listener(get())
          }
        }
      }
    }
  }
}