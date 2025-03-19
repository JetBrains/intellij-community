// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.csat

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import java.time.format.DateTimeFormatter

internal class CsatFeedbackAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      CsatFeedbackSurvey().showNotification(project, false)
    }
  }
}

internal class CsatFeedbackNextDayAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend {
  @Suppress("HardCodedStringLiteral")
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      val nextDate = getNextCsatDay()

      NotificationGroupManager.getInstance().getNotificationGroup("System Messages")
        .createNotification(
          "Next CSAT feedback day is " + nextDate.date.format(DateTimeFormatter.ISO_DATE) + ". " +
          "User is${if (!nextDate.isNewUser) " not " else " "}new.",
          NotificationType.INFORMATION
        )
        .notify(project)
    }
  }
}