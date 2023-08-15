// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings

import com.intellij.ide.IdeBundle
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.providers.vscode.VSCodeTransferSettingsProvider
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.application

private var wasShownOnce = false

class TransferSettingsProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!Registry.`is`(TRANSFER_SETTINGS_REGISTRY_KEY) || application.isUnitTestMode){
      return
    }
    if (wasShownOnce) {
      LOG.info("Balloon was shown in this session once")
      return
    }

    wasShownOnce = true

    val config = DefaultTransferSettingsConfiguration(TransferSettingsDataProvider(VSCodeTransferSettingsProvider()), false)
    val hasVsCode = run {
      config.dataProvider.refresh()
      for (ideVersion in config.dataProvider.orderedIdeVersions) {
        if ((ideVersion as IdeVersion).provider is VSCodeTransferSettingsProvider) {
          return@run true
        }
      }
      false
    }

    if (!hasVsCode) {
      LOG.info("VSCode is not detected")
      return
    }

    val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
      .createNotification("Transfer settings",
                          "Would you like to import your settings from VSCode?",
                          NotificationType.INFORMATION)
      .setSuggestionType(true)

    notification.addAction(NotificationAction.create("Import", com.intellij.util.Consumer {
      notification.hideBalloon()
      TransferSettingsDialog(project, config).show()
    }))
      .addAction(object : NotificationAction(IdeBundle.messagePointer("label.dont.show")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          notification.expire()
          notification.setDoNotAskFor(null)
        }
      })
      .addAction(object : NotificationAction(IdeBundle.messagePointer("label.dont.show")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          notification.expire()
          notification.setDoNotAskFor(null)
        }
      })
      .notify(project)
  }

  companion object {
    const val NOTIFICATION_GROUP = "transferSettings"
    const val TRANSFER_SETTINGS_REGISTRY_KEY = "transferSettings.enabled"
    val LOG = logger<TransferSettingsProjectActivity>()
  }
}