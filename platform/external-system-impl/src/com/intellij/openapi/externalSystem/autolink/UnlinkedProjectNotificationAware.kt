// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.CommonBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker.Companion.LOG
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle.message
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@State(name = "UnlinkedProjectNotification", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class UnlinkedProjectNotificationAware(private val project: Project) : PersistentStateComponent<UnlinkedProjectNotificationAware.State> {
  private val disabledNotifications = Collections.newSetFromMap<String>(ConcurrentHashMap())
  private val notifiedNotifications = DisposableWrapperList<ExternalSystemProjectId>()

  fun notify(unlinkedProjectAware: ExternalSystemUnlinkedProjectAware, externalProjectPath: String) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val systemId = unlinkedProjectAware.systemId
    val projectId = unlinkedProjectAware.getProjectId(externalProjectPath)
    val systemName = systemId.readableName

    if (systemId.id in disabledNotifications) return
    if (projectId in notifiedNotifications) {
      LOG.debug("Unlinked ${projectId.debugName} project notification is already notified")
      return
    }

    val notificationManager = NotificationGroupManager.getInstance()
    val notificationGroup = notificationManager.getNotificationGroup(NOTIFICATION_GROUP_ID)
    val notification = notificationGroup.createNotification(
      message("unlinked.project.notification.title", systemName),
      NotificationType.INFORMATION
    )
    notification.setSuggestionType(true)

    val notificationDisposable = createExtensionDisposable(project, unlinkedProjectAware)
    notifiedNotifications.add(projectId, notificationDisposable)
    notification.whenExpired { Disposer.dispose(notificationDisposable) }
    unlinkedProjectAware.subscribe(project, object : ExternalSystemProjectLinkListener {
      override fun onProjectLinked(externalProjectPath: String) {
        notification.expire()
      }
    }, notificationDisposable)

    notification.addAction(NotificationAction.createSimple(unlinkedProjectAware.getNotificationText()) {
      notification.expire()
      unlinkedProjectAware.linkAndLoadProjectWithLoadingConfirmation(project, externalProjectPath)
    })
    notification.addAction(NotificationAction.createSimple(message("unlinked.project.notification.skip.action")) {
      notification.expire()
      disabledNotifications.add(systemId.id)
    })
    notification.contextHelpAction = object : DumbAwareAction(
      CommonBundle.getHelpButtonText(),
      message("unlinked.project.notification.help.text", systemName), null) {
      override fun actionPerformed(e: AnActionEvent) {}
    }

    notification.notify(project)

    LOG.debug("Notified unlinked ${projectId.debugName} project notification")
  }

  @TestOnly
  fun getProjectsWithNotification(): Set<ExternalSystemProjectId> {
    return notifiedNotifications.toSet()
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
    private const val NOTIFICATION_GROUP_ID = "External System Auto-Link Notification Group"

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
