// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.CommonBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction.createSimpleExpiring
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.ui.ExternalSystemTextProvider
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
@State(name = "UnlinkedProjectNotification", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class UnlinkedProjectNotificationAware(
  private val project: Project
) : PersistentStateComponent<UnlinkedProjectNotificationAware.State> {

  private val disabledNotifications = Collections.newSetFromMap<String>(ConcurrentHashMap())

  private val notifiedNotifications = ConcurrentHashMap<ExternalSystemProjectId, Notification>()

  fun notificationNotify(projectId: ExternalSystemProjectId, callback: () -> Unit) {
    if (projectId.systemId.id in disabledNotifications) {
      LOG.debug(projectId.debugName + ": notification has been disabled")
      return
    }
    if (projectId in notifiedNotifications.keys) {
      LOG.debug(projectId.debugName + ": notification has been already notified")
      return
    }

    val textProvider = ExternalSystemTextProvider.getExtension(projectId.systemId)
    val notificationManager = NotificationGroupManager.getInstance()
    val notificationGroup = notificationManager.getNotificationGroup(NOTIFICATION_GROUP_ID)
    val notificationContent = textProvider.getUPNText(projectId.projectName)
    notifiedNotifications.computeIfAbsent(projectId) {
      notificationGroup.createNotification(notificationContent, NotificationType.INFORMATION)
        .setSuggestionType(true)
        .setNotificationHelp(textProvider.getUPNHelpText())
        .addAction(createSimpleExpiring(textProvider.getUPNLinkActionText()) { callback() })
        .addAction(createSimpleExpiring(textProvider.getUPNSkipActionText()) {
          disabledNotifications.add(projectId.systemId.id)
          LOG.debug(projectId.debugName + ": notification is disabled")
        }).whenExpired {
          notifiedNotifications.remove(projectId)
          LOG.debug(projectId.debugName + ": notification is expired")
        }.apply {
          notify(project)
          LOG.debug(projectId.debugName + ": notification is notified")
        }
    }
  }

  fun notificationExpire(projectId: ExternalSystemProjectId) {
    notifiedNotifications.remove(projectId)?.expire()
  }

  @TestOnly
  fun getProjectsWithNotification(): Set<ExternalSystemProjectId> {
    return notifiedNotifications.keys
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

    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autolink")

    private const val NOTIFICATION_GROUP_ID = "External System Auto-Link Notification Group"

    @JvmStatic
    fun getInstance(project: Project): UnlinkedProjectNotificationAware {
      return project.getService(UnlinkedProjectNotificationAware::class.java)
    }

    @JvmStatic
    fun enableNotifications(project: Project, systemId: ProjectSystemId) {
      getInstance(project).disabledNotifications.remove(systemId.id)
    }

    private fun Notification.setNotificationHelp(help: @NlsActions.ActionDescription String) = apply {
      contextHelpAction = object : DumbAwareAction(CommonBundle.getHelpButtonText(), help, null) {
        override fun actionPerformed(e: AnActionEvent) {}
      }
    }
  }
}
