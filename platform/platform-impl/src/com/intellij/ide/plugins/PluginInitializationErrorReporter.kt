// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.ide.ApplicationActivity
import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.ide.productMode.IdeProductMode
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls

// TODO add trigger on dynamic plugin set change
internal class PluginInitializationErrorReporterStartupActivity : ApplicationActivity {

  val handlers: List<PluginInitializationErrorHandler> by lazy { PluginInitializationErrorHandler.getInstances() }

  override suspend fun execute() {
    if (!IdeProductMode.isBackend) {
      reportPluginErrors()
    }
  }

  internal suspend fun getPluginInitializationErrors(): PluginInitializationErrors {
    val pluginLoadingErrors = ArrayList<@Nls String>()
    val pluginNamesToEnable = ArrayList<String>()
    val pluginNamesToDisable = ArrayList<String>()

    for (handler in PluginInitializationErrorHandler.getInstances()) {
      val initializationErrors = handler.getPluginInitializationErrors()
      pluginLoadingErrors.addAll(initializationErrors.pluginErrors)
      pluginNamesToEnable.addAll(initializationErrors.pluginNamesToEnable)
      pluginNamesToDisable.addAll(initializationErrors.pluginNamesToDisable)
    }

    return PluginInitializationErrors(pluginLoadingErrors, pluginNamesToEnable, pluginNamesToDisable)
  }

  internal suspend fun reportPluginErrors() {
    val errors = getPluginInitializationErrors()

    val pluginErrors = errors.pluginErrors
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

    serviceAsync<NotificationGroupManager>().getNotificationGroup("Plugin Error")
      .createNotification(title, content, NotificationType.ERROR)
      .addActions(actions)
      .notify(null)
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
        handlers.forEach { it.enableDeferredPlugins() }
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
        handlers.forEach { it.disableDeferredPlugins() }
      }
    }
  }

  internal fun prepareEditAction(): AnAction {
    return NotificationAction.createSimpleExpiring(CoreBundle.message("link.text.open.plugin.manager")) {
      val configurable = PluginManagerConfigurable()
      ShowSettingsUtil.getInstance().editConfigurable(
        null as Project?,
        configurable,
        Runnable {
          configurable.openInstalledTab("/invalid") // TODO: does nothing, does not set query
        }
      )
    }
  }
}