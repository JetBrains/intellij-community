// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file: JvmName("SuggestUsingRunDashBoardUtil")
package com.intellij.execution

import com.intellij.CommonBundle
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.icons.AllIcons
import com.intellij.lang.LangBundle
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
      Notifications.Bus.notify(
        SuggestDashboardNotification(project, typesToAdd.toSet(), RunDashboardManager.getInstance(project).toolWindowId), project)
    }
  }
}

private const val suggestRunDashboardId = "Services Tool Window"

private class SuggestDashboardNotification(
  private val project: Project,
  types: Set<ConfigurationType>,
  toolWindowId: String
) : Notification(
  suggestRunDashboardId,
  LangBundle.message("notification.title.use.toolwindow", toolWindowId),
  LangBundle.message("notification.suggest.dashboard", toolWindowId, toolWindowId,
                     types.joinToString(prefix = "<b>", postfix = "</b>", separator = "<br>") { it.configurationTypeDescription }),
  NotificationType.INFORMATION
) {
  init {
    icon = AllIcons.RunConfigurations.TestState.Run
    addAction(
      NotificationAction.create(CommonBundle.message("button.without.mnemonic.yes")) { _ ->
      ApplicationManager.getApplication().invokeLater {
        runWriteAction {
          val runDashboardManager = RunDashboardManager.getInstance(project)
          runDashboardManager.types = runDashboardManager.types + types.map { it.id }
        }
      }
      expire()
    })
    addAction(NotificationAction.create(LangBundle.message("button.not.this.time.text")) { _ ->
      expire()
    })
    addAction(NotificationAction.create(LangBundle.message("button.do.not.ask.again.text")) { _ ->
      NotificationsConfiguration.getNotificationsConfiguration().changeSettings(
        suggestRunDashboardId, NotificationDisplayType.NONE, true, false
      )
      expire()
    })
  }
}
