// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.plugin.freeze

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.RestartDialogImpl
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function
import javax.swing.JComponent

internal class PluginFreezeNotifier : EditorNotificationProvider {
  private val freezeWatcher = PluginFreezeWatcher.getInstance()
  private val freezeStorageService = PluginsFreezesService.getInstance()

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    val frozenPlugin = freezeWatcher.latestFrozenPlugin ?: return null
    val pluginDescriptor = PluginManagerCore.getPlugin(frozenPlugin) ?: return null
    if (pluginDescriptor.isBundled) return null
    if (freezeStorageService.shouldBeIgnored(frozenPlugin)) return null

    freezeStorageService.setLatestFreezeDate(frozenPlugin)

    return Function {
      EditorNotificationPanel(EditorNotificationPanel.Status.Warning).apply {
        text = PluginFreezeBundle.message("notification.content.plugin.caused.freeze.detected", pluginDescriptor.name)
          createActionLabel(PluginFreezeBundle.message("action.disable.plugin.text")) {
            disablePlugin(frozenPlugin)
          }
          createActionLabel(PluginFreezeBundle.message("action.ignore.plugin.text")) {
            freezeStorageService.mutePlugin(frozenPlugin)
            closePanel(project)
          }
          createActionLabel(PluginFreezeBundle.message("action.close.panel.text")) {
            closePanel(project)
          }
        }
    }
  }

  //TODO support dynamic plugins
  private fun disablePlugin(frozenPlugin: PluginId) {
    val pluginDisabled = PluginManagerCore.disablePlugin(frozenPlugin)
    if (pluginDisabled) {
      RestartDialogImpl.showRestartRequired()
    }
  }

  private fun closePanel(project: Project) {
    freezeWatcher.latestFrozenPlugin = null
    FileEditorManager.getInstance(project).openFiles.forEach { it -> EditorNotifications.getInstance(project).updateNotifications(it) }
  }

}