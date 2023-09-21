// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeCoreBundle
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.newui.PluginLogo
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.io.IOException
import java.util.*

private const val IGNORE_LANGUAGE_DETECTOR_PROPERTY_NAME = "LANGUAGE_DETECTOR_ASKED_BEFORE"

private class LanguagePluginDetectionStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val application = ApplicationManagerEx.getApplicationEx()
    if (application == null
        || application.isUnitTestMode
        || application.isHeadlessEnvironment
        || PropertiesComponent.getInstance().ignoreLanguageDetector) {
      return
    }

    val pluginNode = findLanguagePluginToInstall() ?: return
    val icon = PluginLogo.getIcon(pluginNode, false, false, false)
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Language Plugins Notifications")
      .createNotification(
        ApplicationBundle.message("notification.content.available", pluginNode.name),
        NotificationType.INFORMATION,
      ).setSuggestionType(true)
      .setIcon(IconUtil.scale(icon, null, 32f/icon.iconWidth))
      .addAction(
        NotificationAction.create(ApplicationBundle.message("notification.content.install.restart")) { _, notification ->
          installAndEnable(project, setOf(pluginNode.pluginId)) {
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
private fun findLanguagePluginToInstall(): PluginNode? {
  try {
    val locale = Locale.getDefault()
    if (locale == Locale.ENGLISH
        || locale == Locale.UK
        || locale == Locale.US
        || locale == Locale.CANADA) {
      // no need to visit Marketplace for them
      return null
    }

    val requests = MarketplaceRequests.getInstance()

    fun getLanguagePlugins(implementationName: String) = requests.getFeatures(
      featureType = "com.intellij.locale",
      implementationName = implementationName,
    )

    val pattern = if (SystemInfoRt.isMac) {
      if (Locale.SIMPLIFIED_CHINESE.language.equals(locale.language)
          && (Locale.SIMPLIFIED_CHINESE.country.equals(locale.country) || Locale.TAIWAN.country.equals(locale.country)))
        if (locale.script == "Hans") Locale.SIMPLIFIED_CHINESE.toLanguageTag()
        else locale.language
      else locale.language
    }
    else {
      if (Locale.SIMPLIFIED_CHINESE.language.equals(locale.language) && Locale.SIMPLIFIED_CHINESE.country.equals(locale.country))
        Locale.SIMPLIFIED_CHINESE.toLanguageTag()
      else locale.language
    }

    val matchedLanguagePlugins = getLanguagePlugins(pattern)

    return requests.searchPlugins(
      query = "tags=Language%20Pack",
      count = 10,
    ).firstOrNull { node ->
      matchedLanguagePlugins.any { it.pluginId == node.pluginId.idString }
      && !PluginManagerCore.isPluginInstalled(node.pluginId)
    }
  }
  catch (e: IOException) {
    logger<LanguagePluginDetectionStartupActivity>()
      .info("Failed to detect recommended language plugin: ${e.message}")
    return null
  }
}