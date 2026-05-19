// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable

private val AI_ASSISTANT_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.ml.llm")

/**
 * Shows a balloon notification informing the user that the AI Assistant plugin is required to
 * launch ACP agents, with an action that opens the plugin marketplace and installs it.
 *
 * Intended for clients that catch [AcpProcessLaunchException.AiAssistantPluginMissing] and want to
 * surface a recoverable error to the user without duplicating the install-plugin wiring.
 */
fun notifyAiAssistantPluginMissing(project: Project, agentName: String) {
  val installAction = NotificationAction.createSimpleExpiring(
    AcpPlatformBundle.message("notification.aia.plugin.missing.action.install")
  ) {
    installAndEnable(project, setOf(AI_ASSISTANT_PLUGIN_ID), showDialog = true) {}
  }
  NotificationGroupManager.getInstance()
    .getNotificationGroup("Plugins Suggestion")
    .createNotification(
      AcpPlatformBundle.message("notification.aia.plugin.missing.title"),
      AcpPlatformBundle.message("notification.aia.plugin.missing.content", agentName),
      NotificationType.WARNING,
    )
    .addAction(installAction)
    .notify(project)
}
