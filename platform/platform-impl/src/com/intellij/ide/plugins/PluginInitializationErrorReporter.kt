// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerOpenSourceEnum
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.util.ui.RawSwingDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

// TODO add trigger on dynamic plugin set change
internal class PluginLoadingProblemsStartupReporter : AppLifecycleListener {
  val handlers: List<PluginLoadingProblemsNotificationHandler> by lazy { PluginLoadingProblemsNotificationHandler.getInstances() }

  override fun appStarted() {
    service<PluginManagerCoroutineScopeHolder>().coroutineScope.launch {
      if (!IdeProductMode.isBackend) {
        reportPluginLoadingProblems()
      }
    }
  }

  internal suspend fun getPluginLoadingProblems(): PluginLoadingProblemsNotificationData {
    val pluginLoadingErrors = ArrayList<@Nls String>()
    val pluginNamesToEnable = ArrayList<String>()
    val pluginNamesToDisable = ArrayList<String>()

    for (handler in PluginLoadingProblemsNotificationHandler.getInstances()) {
      val loadingProblems = handler.getPluginLoadingProblemsForNotification()
      pluginLoadingErrors.addAll(loadingProblems.pluginLoadingProblemsForNotification)
      pluginNamesToEnable.addAll(loadingProblems.pluginNamesToEnable)
      pluginNamesToDisable.addAll(loadingProblems.pluginNamesToDisable)
    }

    return PluginLoadingProblemsNotificationData(pluginLoadingErrors, pluginNamesToEnable, pluginNamesToDisable)
  }

  internal suspend fun reportPluginLoadingProblems() {
    val errors = getPluginLoadingProblems()

    val pluginErrors = errors.pluginLoadingProblemsForNotification
    if (pluginErrors.isEmpty()) {
      return
    }
    val title = IdeBundle.message("title.plugin.error")
    val content = HtmlBuilder().appendWithSeparators(HtmlChunk.p(), pluginErrors.map { HtmlChunk.text(it) }).toString()

    val actions: MutableList<AnAction> = ArrayList()
    actions += prepareEditAction()
    val pluginsToEnable = errors.pluginNamesToEnable
    val pluginsToDisable = errors.pluginNamesToDisable
    if (pluginsToEnable.isNotEmpty()) {
      actions += prepareEnableAction(pluginsToEnable)
    }
    if (pluginsToDisable.isNotEmpty()) {
      actions += prepareDisableAction(pluginsToDisable)
    }

    val notificationGroupManager = serviceAsync<NotificationGroupManager>()

    withContext(RawSwingDispatcher) {
      notificationGroupManager.getNotificationGroup("Plugin Error")
        .createNotification(title, content, NotificationType.ERROR)
        .addActions(actions)
        .notify(null)
    }
  }

  internal fun prepareEnableAction(pluginsToEnable: Collection<String>): AnAction {
    assert(pluginsToEnable.isNotEmpty())
    val pluginNameToEnable = pluginsToEnable.singleOrNull()
    val enableMessage = if (pluginNameToEnable != null) {
      CoreBundle.message("link.text.enable.plugin", pluginNameToEnable)
    }
    else {
      CoreBundle.message("link.text.enable.all.necessary.plugins")
    }
    return NotificationAction.createSimpleExpiring(enableMessage) {
      service<PluginManagerCoroutineScopeHolder>().coroutineScope.launch {
        handlers.forEach { it.onEnableClickInNotification() }
      }
    }
  }

  internal fun prepareDisableAction(pluginsToDisable: Collection<String>): AnAction {
    assert(pluginsToDisable.isNotEmpty())
    val pluginToDisable = pluginsToDisable.singleOrNull()
    val disableMessage = if (pluginToDisable != null) {
      CoreBundle.message("link.text.disable.plugin", pluginToDisable)
    }
    else {
      CoreBundle.message("link.text.disable.not.loaded.plugins")
    }
    return NotificationAction.createSimpleExpiring(disableMessage) {
      service<PluginManagerCoroutineScopeHolder>().coroutineScope.launch {
        handlers.forEach { it.onDisableClickInNotification() }
      }
    }
  }

  internal fun prepareEditAction(): AnAction {
    return NotificationAction.createSimpleExpiring(CoreBundle.message("link.text.open.plugin.manager")) {
      PluginManagerConfigurableUtils.showInstalledTabWithSearch(null, "/invalid", PluginManagerOpenSourceEnum.NOTIFICATION)
    }
  }
}