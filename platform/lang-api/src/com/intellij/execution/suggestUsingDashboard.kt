// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file: JvmName("SuggestUsingRunDashBoardUtil")
package com.intellij.execution

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.icons.AllIcons
import com.intellij.notification.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project

/**
* If Run Dashboard is not configured for [configurationTypes], show [Notification] allowing to enable dashboard for those configurations.
**/
fun promptUserToUseRunDashboard(project: Project, configurationTypes: Collection<ConfigurationType>) {
  ApplicationManager.getApplication().invokeLater {
    val currentTypes = RunDashboardManager.getInstance(project).types
    val typesToAdd = configurationTypes.filter {
      it.id !in currentTypes
    }
    if (typesToAdd.isNotEmpty()) {
      Notifications.Bus.notify(SuggestDashboardNotification(project, typesToAdd.toSet()), project)
    }
  }
}

private const val suggestRunDashboardId = "Suggest Run Dashboard"

private class SuggestDashboardNotification(
  private val project: Project,
  private val types: Set<ConfigurationType>
) : Notification(
  suggestRunDashboardId,
  AllIcons.General.Run,
  "Use Run Dashboard?",
  null,
  "Run Dashboard is convenient for viewing results of multiple run configuration at once. " +
  "Add the following configuration types to Run Dashboard:<br>" +
  types.joinToString(prefix = "<b>", postfix = "</b>", separator = "<br>") { it.configurationTypeDescription } + "<br>?",
  NotificationType.INFORMATION,
  { _, _ -> }
) {
  init {
    addAction(NotificationAction.create("Yes") { _ ->
      ApplicationManager.getApplication().invokeLater {
        runWriteAction {
          val runDashboardManager = RunDashboardManager.getInstance(project)
          runDashboardManager.types = runDashboardManager.types + types.map { it.id }
        }
      }
      expire()
    })
    addAction(NotificationAction.create("Not this time") { _ ->
      expire()
    })
    addAction(NotificationAction.create("Do not ask again") { _ ->
      NotificationsConfiguration.getNotificationsConfiguration().changeSettings(
        suggestRunDashboardId, NotificationDisplayType.NONE, true, false
      )
      expire()
    })
  }
}