// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.util.*
import java.util.function.BiConsumer

private const val LOCALE_FEATURE_TYPE = "com.intellij.locale"
private const val GROUP_ID = "Language Plugins Notifications"

internal class LanguagePluginDetectionStartupActivity : StartupActivity.Background {

  @RequiresBackgroundThread
  override fun runActivity(project: Project) {
    val application = ApplicationManagerEx.getApplicationEx()
    if (application.isUnitTestMode ||
        application.isHeadlessEnvironment) {
      return
    }

    val pluginId = findLanguagePluginToInstall() ?: return

    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup(GROUP_ID)
      .createNotification(
        ApplicationBundle.message("notification.title.language.plugin.enable", ApplicationInfo.getInstance().fullApplicationName),
        NotificationType.INFORMATION,
      )

    val action = createSwitchAndRestartAction { _, _ ->
      installAndEnable(project, setOf(pluginId)) {
        notification.expire()
        application.restart(true)
      }
    }

    notification.addAction(action).notify(project)
  }

  companion object {

    private fun getLanguagePlugins(implementationName: String) = MarketplaceRequests.getInstance().getFeatures(
      LOCALE_FEATURE_TYPE,
      implementationName,
    )

    private fun createSwitchAndRestartAction(action: BiConsumer<in AnActionEvent, in Notification>) = NotificationAction.create(
      ApplicationBundle.message("notification.action.language.plugin.install.and.enable"),
      action,
    )

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
  }
}
