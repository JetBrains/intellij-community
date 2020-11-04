// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser
import java.util.*

class LanguageDetector : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (!Experiments.getInstance().isFeatureEnabled("language.detect.notification")) return

    // todo provide structure search when it's supported by marketplace
    val query = "search=Language%20Pack%20EAP"
    val list = MarketplaceRequests.getInstance().searchPlugins(query, 10)

    // todo match locale
    //val languageTag = locale.toLanguageTag()
    val languageTag = "zh"
    val pluginNode = list.find { it.pluginId.idString.substringAfter("com.intellij.") == languageTag }
    val pluginId = pluginNode?.pluginId

    if (pluginId == null) return
    if (PluginManagerCore.isPluginInstalled(pluginId)) return

    val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Language Plugins Notifications")
    val app = ApplicationManagerEx.getApplicationEx()

    val notificationTitle = ApplicationBundle.message("notification.title.language.plugin.enable", ApplicationInfo.getInstance().fullApplicationName)
    val notification = notificationGroup.createNotification(notificationTitle, null, null)
    val action = NotificationAction.create(ApplicationBundle.message("notification.action.language.plugin.install.and.enable"), { _, _ ->
      PluginsAdvertiser.installAndEnable(project, setOf(pluginId), false, Runnable {
        notification.expire()
        app.restart(true)
      })
    })
    notification.addAction(action).notify(project)
  }
}