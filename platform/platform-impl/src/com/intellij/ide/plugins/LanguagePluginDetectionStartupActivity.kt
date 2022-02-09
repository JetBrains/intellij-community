// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.FeatureImpl
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import java.io.IOException
import java.util.*
import java.util.function.BiConsumer

private class LanguagePluginDetectionStartupActivity : StartupActivity.Background {
  override fun runActivity(project: Project) {
    val application = ApplicationManagerEx.getApplicationEx()
    if (application.isUnitTestMode || application.isHeadlessEnvironment) {
      return
    }

    val pluginId = try {
      findLanguagePluginToInstall() ?: return
    }
    catch (e: IOException) {
      LOG.info("Failed to detect recommended language plugin: ${e.message}")
      return
    }

    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("Language Plugins Notifications")
      .createNotification(
        ApplicationBundle.message("notification.title.language.plugin.enable", ApplicationInfo.getInstance().fullApplicationName),
        NotificationType.INFORMATION,
      )
      .setSuggestionType(true)

    notification.addAction(createSwitchAndRestartAction { _, _ ->
      installAndEnable(project, setOf(pluginId)) {
        notification.expire()
        application.restart(true)
      }
    }).notify(project)
  }

  companion object {
    val LOG = Logger.getInstance(LanguagePluginDetectionStartupActivity::class.java)
  }
}

private fun getLanguagePlugins(implementationName: String): List<FeatureImpl> {
  return MarketplaceRequests.getInstance().getFeatures("com.intellij.locale", implementationName)
}

private fun createSwitchAndRestartAction(action: BiConsumer<AnActionEvent, Notification>): NotificationAction {
  return NotificationAction.create(ApplicationBundle.message("notification.action.language.plugin.install.and.enable"), action)
}

private fun findLanguagePluginToInstall(): PluginId? {
  val locale = Locale.getDefault()
  val matchedLanguagePlugins = getLanguagePlugins(locale.toLanguageTag())
    .ifEmpty { getLanguagePlugins(locale.language) }

  return MarketplaceRequests.getInstance()
    .searchPlugins("tags=Language%20Pack", 10)
    .map { it.pluginId }
    .firstOrNull { pluginId ->
      matchedLanguagePlugins.any { it.pluginId == pluginId.idString }
      && !PluginManagerCore.isPluginInstalled(pluginId)
    }
}
