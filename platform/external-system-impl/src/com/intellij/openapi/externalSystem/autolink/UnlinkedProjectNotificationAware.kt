// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.CommonBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationDisplayType.STICKY_BALLOON
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle.message
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.DisposableWrapperList

@State(name = "UnlinkedProjectNotification", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class UnlinkedProjectNotificationAware(private val project: Project) : PersistentStateComponent<UnlinkedProjectNotificationAware.State> {

  private val disabledNotifications = ContainerUtil.newConcurrentSet<String>()
  private val notifiedNotifications = DisposableWrapperList<String>()

  fun notify(unlinkedProjectAware: ExternalSystemUnlinkedProjectAware, externalProjectPath: String) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val systemId = unlinkedProjectAware.systemId.id
    val systemName = unlinkedProjectAware.systemId.readableName

    if (systemId in disabledNotifications) return
    if (systemId in notifiedNotifications) return

    val notification = NOTIFICATION_GROUP.createNotification(
      message("unlinked.project.notification.title", systemName),
      NotificationType.INFORMATION
    )

    val notificationDisposable = Disposer.newDisposable()
    Disposer.register(project, notificationDisposable)
    notifiedNotifications.add(systemId, notificationDisposable)
    notification.whenExpired { Disposer.dispose(notificationDisposable) }
    unlinkedProjectAware.subscribe(project, object : ExternalSystemProjectListener {
      override fun onProjectLinked(externalProjectPath: String) {
        notification.expire()
      }
    }, notificationDisposable)

    notification.addAction(NotificationAction.createSimpleExpiring(message("unlinked.project.notification.load.action", systemName)) {
      notification.expire()
      unlinkedProjectAware.linkAndLoadProject(project, externalProjectPath)
    })
    notification.addAction(NotificationAction.createSimple(message("unlinked.project.notification.skip.action")) {
      notification.expire()
      disabledNotifications.add(systemId)
    })
    notification.contextHelpAction = object : DumbAwareAction(
      CommonBundle.getHelpButtonText(),
      message("unlinked.project.notification.help.text", systemName), null) {
      override fun actionPerformed(e: AnActionEvent) {}
    }

    notification.notify(project)
  }

  override fun getState(): State {
    return State(disabledNotifications)
  }

  override fun loadState(state: State) {
    disabledNotifications.clear()
    disabledNotifications.addAll(state.disabledNotifications)
  }

  data class State(var disabledNotifications: Set<String> = emptySet())

  companion object {
    private val NOTIFICATION_GROUP = NotificationGroup("External System Auto-Link Notification Group", STICKY_BALLOON, true)

    @JvmStatic
    fun getInstance(project: Project): UnlinkedProjectNotificationAware {
      return project.getService(UnlinkedProjectNotificationAware::class.java)
    }

    @JvmStatic
    fun enableNotifications(project: Project, systemId: ProjectSystemId) {
      getInstance(project).disabledNotifications.remove(systemId.id)
    }
  }
}