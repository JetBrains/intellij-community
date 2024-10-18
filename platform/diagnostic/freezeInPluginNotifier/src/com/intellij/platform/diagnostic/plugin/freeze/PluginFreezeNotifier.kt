// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.plugin.freeze

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.RestartDialogImpl
import java.util.function.Function
import javax.swing.JComponent

internal class PluginFreezeNotifier : EditorNotificationProvider {
  private val freezeWatcher = PluginFreezeWatcher.getInstance()
  private val freezeStorageService = PluginsFreezesService.getInstance()

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!Registry.get("ide.diagnostics.notification.freezes.in.plugins").asBoolean()) return null

    val frozenPlugin = freezeWatcher.getFreezeReason() ?: return null
    val pluginDescriptor = PluginManagerCore.getPlugin(frozenPlugin) ?: return null
    val application = ApplicationManager.getApplication()
    if (pluginDescriptor.isImplementationDetail || ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(frozenPlugin)) return null
    if (pluginDescriptor.isBundled && !application.isInternal) return null

    return Function {
      EditorNotificationPanel(EditorNotificationPanel.Status.Warning).apply {
        text = PluginFreezeBundle.message("notification.content.plugin.caused.freeze.detected", pluginDescriptor.name)
          createActionLabel(PluginFreezeBundle.message("action.disable.plugin.text")) {
            disablePlugin(frozenPlugin)

            LifecycleUsageTriggerCollector.pluginDisabledOnFreeze(frozenPlugin)
          }
          createActionLabel(PluginFreezeBundle.message("action.ignore.plugin.text")) {
            freezeStorageService.mutePlugin(frozenPlugin)

            LifecycleUsageTriggerCollector.pluginFreezeIgnored(frozenPlugin)

            closePanel(project)
          }
        createActionLabel(PluginFreezeBundle.message("action.open.issue.tracker.text")) {
          openIssueTracker(project, pluginDescriptor)

          LifecycleUsageTriggerCollector.pluginIssueTrackerOpened()
        }
          createActionLabel(PluginFreezeBundle.message("action.close.panel.text")) {
            closePanel(project)
          }
        }
    }
  }

  // TODO support dynamic plugins
  private fun disablePlugin(frozenPlugin: PluginId) {
    val pluginDisabled = PluginManagerCore.disablePlugin(frozenPlugin)
    if (pluginDisabled) {
      RestartDialogImpl.showRestartRequired()
    }
  }

  private fun openIssueTracker(project: Project, pluginDescriptor: IdeaPluginDescriptor) {
    runWithModalProgressBlocking(project, PluginFreezeBundle.message("progress.title.opening.issue.tracker")) {
      PluginIssueTrackerResolver.getMarketplaceBugTrackerUrl(pluginDescriptor)?.let {
        BrowserUtil.open(it)
      }
    }
  }

  private fun closePanel(project: Project) {
    freezeWatcher.reset()
    EditorNotifications.getInstance(project).updateAllNotifications()
  }
}