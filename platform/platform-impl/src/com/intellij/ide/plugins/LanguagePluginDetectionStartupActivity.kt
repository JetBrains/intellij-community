// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeCoreBundle
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.io.IOException
import java.util.*

private const val IGNORE_LANGUAGE_DETECTOR_PROPERTY_NAME = "LANGUAGE_DETECTOR_ASKED_BEFORE"

private class LanguagePluginDetectionStartupActivity : StartupActivity.Background {

  override fun runActivity(project: Project) {
    val application = ApplicationManagerEx.getApplicationEx()
    if (application == null
        || application.isUnitTestMode
        || application.isHeadlessEnvironment
        || PropertiesComponent.getInstance().ignoreLanguageDetector) {
      return
    }

    val pluginId = findLanguagePluginToInstall() ?: return

    NotificationGroupManager.getInstance()
      .getNotificationGroup("Language Plugins Notifications")
      .createNotification(
        ApplicationBundle.message("notification.title.language.plugin.enable", ApplicationInfo.getInstance().fullApplicationName),
        NotificationType.INFORMATION,
      ).setSuggestionType(true)
      .addAction(
        NotificationAction.create(ApplicationBundle.message("notification.action.language.plugin.install.and.enable")) { _, notification ->
          installAndEnable(project, setOf(pluginId)) {
            notification.expire()
            ApplicationManagerEx.getApplicationEx().restart(true)
          }
        }
      ).addAction(
        NotificationAction.create(IdeCoreBundle.message("dialog.options.do.not.ask")) { _, notification ->
          PropertiesComponent.getInstance().ignoreLanguageDetector = true
          notification.expire()
        }
      ).notify(project)
  }
}

private var PropertiesComponent.ignoreLanguageDetector: Boolean
  get() = getBoolean(IGNORE_LANGUAGE_DETECTOR_PROPERTY_NAME, false)
  set(value) = setValue(IGNORE_LANGUAGE_DETECTOR_PROPERTY_NAME, value)

@RequiresBackgroundThread
private fun findLanguagePluginToInstall(): PluginId? = try {
  val requests = MarketplaceRequests.getInstance()

  fun getLanguagePlugins(implementationName: String) = requests.getFeatures(
    featureType = "com.intellij.locale",
    implementationName = implementationName,
  )

  val locale = Locale.getDefault()
  val matchedLanguagePlugins = getLanguagePlugins(locale.toLanguageTag())
    .ifEmpty { getLanguagePlugins(locale.language) }

  requests.searchPlugins(
    query = "tags=Language%20Pack",
    count = 10,
  ).map {
    it.pluginId
  }.firstOrNull { pluginId ->
    matchedLanguagePlugins.any { it.pluginId == pluginId.idString }
    && !PluginManagerCore.isPluginInstalled(pluginId)
  }
}
catch (e: IOException) {
  logger<LanguagePluginDetectionStartupActivity>()
    .info("Failed to detect recommended language plugin: ${e.message}")
  null
}