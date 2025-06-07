// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.localization

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification

@Service(Service.Level.APP)
internal class LocalizationFeedbackNotificationService {
  companion object {
    fun getInstance(): LocalizationFeedbackNotificationService = service<LocalizationFeedbackNotificationService>()
  }

  private val notifications = mutableListOf<Notification>()

  fun showNotification() {
    ProjectManager.getInstance().openProjects.forEach {
      showNotification(it)
    }
  }

  private fun showNotification(project: Project) {
    val notification = buildNotification {
      LocalizationFeedbackDialog(it, LocalizationFeedbackService.isTesting()).show()
    }

    notifications.add(notification)
    Disposer.register(project) {
      notifications.remove(notification)
    }

    notification.notify(project)
  }

  private fun buildNotification(action: (Project) -> Unit): Notification {
    val notification = RequestFeedbackNotification(
      "Feedback In IDE",
      LocalizationFeedbackBundle.message("notification.title"),
      LocalizationFeedbackBundle.message("notification.text"))

    notification.addAction(NotificationAction.createExpiring(LocalizationFeedbackBundle.message("notification.respond.button")) { e, _ ->
      val project = e.project ?: return@createExpiring
      LocalizationFeedbackService.getInstance().setInteraction()
      action(project)
      notifications.forEach { it.expire() }
      notifications.removeAll { true }
    })

    notification.addAction(NotificationAction.createSimpleExpiring(LocalizationFeedbackBundle.message("notification.dotNotShow.button")) {
      notifications.forEach { it.expire() }
      notifications.removeAll { true }
      LocalizationFeedbackService.getInstance().setInteraction()
    })

    return notification
  }
}