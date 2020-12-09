// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.marketplace.FeatureImpl
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser
import java.util.*

class LanguageDetector : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (!Experiments.getInstance().isFeatureEnabled("language.detect.notification")) return

    findPlugin()?.let { notify(project, it) }
  }

  companion object {
    private fun matchedLanguagePlugins(): List<FeatureImpl> {
      val features = getFeatures(Locale.getDefault().toLanguageTag())
      if (features.isNotEmpty()) return features

      return getFeatures(Locale.getDefault().language)
    }

    private fun getFeatures(languageTag: String): List<FeatureImpl> {
      val build = MarketplaceRequests.getInstance().getBuildForPluginRepositoryRequests()
      val params = mapOf("featureType" to "com.intellij.locale", "implementationName" to languageTag, "build" to build)
      return MarketplaceRequests.getInstance().getFeatures(params)
    }

    private fun verifiedLanguagePlugins() = MarketplaceRequests.getInstance().searchPlugins("tags=Language%20Pack", 10)

    private fun installAction(project: Project, matchedVerifiedPlugin: PluginNode, notification: Notification) =
      NotificationAction.create(ApplicationBundle.message("notification.action.language.plugin.install.and.enable")) { _, _ ->
        PluginsAdvertiser.installAndEnable(project, setOf(matchedVerifiedPlugin.pluginId), false, Runnable {
          notification.expire()
          ApplicationManagerEx.getApplicationEx().restart(true)
        })
      }

    private fun findPlugin() = verifiedLanguagePlugins()
      .firstOrNull {
        matchedLanguagePlugins().any { matched -> matched.pluginId == it.pluginId.idString }
        && !PluginManagerCore.isPluginInstalled(it.pluginId)
      }

    private fun notify(project: Project, plugin: PluginNode) {
      val notificationTitle = ApplicationBundle.message("notification.title.language.plugin.enable",
                                                        ApplicationInfo.getInstance().fullApplicationName)
      NotificationGroupManager.getInstance().getNotificationGroup("Language Plugins Notifications")
        .createNotification(notificationTitle, null, null)
        .also { it.addAction(installAction(project, plugin, it)) }
        .notify(project)
    }
  }
}