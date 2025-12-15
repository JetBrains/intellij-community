// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginInitializationErrorReporter.reportPluginErrors
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ex.WindowManagerEx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginInitializationErrorReporter {
  @Synchronized
  @JvmStatic
  fun onEnable(enabled: Boolean): Boolean {
    val (pluginsToEnable, pluginsToDisable) = PluginManagerCore.consumeStartupActionsPluginsToEnableDisable()
    val pluginIds = if (enabled) pluginsToEnable else pluginsToDisable
    if (pluginIds.isEmpty()) {
      return false
    }
    val descriptors = ArrayList<IdeaPluginDescriptorImpl>()
    for (descriptor in PluginManagerCore.getPluginSet().allPlugins) {
      if (descriptor.getPluginId() in pluginIds) {
        descriptor.isMarkedForLoading = enabled
        descriptors.add(descriptor)
      }
    }
    val pluginEnabler = PluginEnabler.getInstance()
    if (enabled) {
      pluginEnabler.enable(descriptors)
    }
    else {
      pluginEnabler.disable(descriptors)
    }
    return true
  }


  @ApiStatus.Internal
  fun onEvent(description: String) {
    when (description) {
      PluginManagerCore.DISABLE -> onEnable(false)
      PluginManagerCore.ENABLE -> {
        if (onEnable(true)) {
          PluginManagerMain.notifyPluginsUpdated(null)
        }
      }
      PluginManagerCore.EDIT -> {
        val frame: IdeFrame? = WindowManagerEx.getInstanceEx().findFrameFor(null)
        PluginManagerConfigurable.showPluginConfigurable(frame?.getComponent(), null, mutableListOf<PluginId?>())
      }
    }
  }


  internal suspend fun reportPluginErrors() {
    val pluginErrors = PluginManagerCore.getAndClearPluginLoadingErrors()
    if (pluginErrors.isEmpty()) {
      return
    }

    withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
      val title = IdeBundle.message("title.plugin.error")
      val pluginErrorMessages = pluginErrors.map { it.htmlMessage }.toMutableList()
      val actions = linksToActions(pluginErrorMessages)
      val content = HtmlBuilder().appendWithSeparators(HtmlChunk.p(), pluginErrorMessages).toString()
      @Suppress("DEPRECATION")
      serviceAsync<NotificationGroupManager>().getNotificationGroup("Plugin Error")
        .createNotification(title, content, NotificationType.ERROR)
        .setListener { notification, event ->
          notification.expire()
          onEvent(event.description)
        }
        .addActions(actions)
        .notify(null)
    }
  }

  private fun linksToActions(errors: MutableList<HtmlChunk>): Collection<AnAction> {
    val link = "<a href=\""
    val actions = ArrayList<AnAction>()

    while (!errors.isEmpty()) {
      val builder = StringBuilder()
      errors[errors.lastIndex].appendTo(builder)
      val error = builder.toString()

      if (error.startsWith(link)) {
        val descriptionEnd = error.indexOf('"', link.length)
        val description = error.substring(link.length, descriptionEnd)
        @Suppress("HardCodedStringLiteral")
        val text = error.substring(descriptionEnd + 2, error.lastIndexOf("</a>"))
        errors.removeAt(errors.lastIndex)

        actions.add(NotificationAction.createSimpleExpiring(text) {
          onEvent(description)
        })
      }
      else {
        break
      }
    }

    return actions
  }
}

// TODO add trigger on dynamic plugin set change
internal class PluginInitializationErrorReporterStartupTrigger : ApplicationActivity {
  override suspend fun execute() {
    reportPluginErrors()
  }
}