// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.notification

import com.intellij.notification.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.NonNls

@Service(Service.Level.PROJECT)
class CollaborationToolsNotifier(private val project: Project) {
  private val NOTIFICATION_GROUP_ID: NotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup("VCS Hosting Integrations")

  fun notifyBalloon(
    displayId: @NonNls String?,
    title: @NlsContexts.NotificationTitle String,
    message: @NlsContexts.NotificationContent String,
    vararg actions: NotificationAction
  ): Notification {
    val notification = createNotification(NOTIFICATION_GROUP_ID, displayId, title, message, NotificationType.INFORMATION).apply {
      actions.forEach { action -> addAction(action) }
      notify(project)
    }

    return notification
  }

  private fun createNotification(
    notificationGroup: NotificationGroup,
    displayId: @NonNls String?,
    title: @NlsContexts.NotificationTitle String,
    message: @NlsContexts.NotificationContent String,
    type: NotificationType
  ): Notification {
    val notification = notificationGroup.createNotification(title, message, type)
    if (!displayId.isNullOrEmpty()) notification.setDisplayId(displayId)
    return notification
  }

  companion object {
    fun getInstance(project: Project): CollaborationToolsNotifier {
      return project.service<CollaborationToolsNotifier>()
    }
  }
}