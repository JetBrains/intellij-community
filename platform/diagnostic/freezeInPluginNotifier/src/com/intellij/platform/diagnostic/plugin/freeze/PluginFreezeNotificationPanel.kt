// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.plugin.freeze

import com.intellij.diagnostic.FreezeNotifier
import com.intellij.diagnostic.IdeErrorsDialog
import com.intellij.diagnostic.MessagePool
import com.intellij.diagnostic.ThreadDump
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.isVendorJetBrains
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.nio.file.Path
import java.util.*
import java.util.function.Function
import javax.swing.JComponent

internal class PluginFreezeNotifier: FreezeNotifier {
  override fun notifyFreeze(event: IdeaLoggingEvent, currentDumps: Collection<ThreadDump>, reportDir: Path, durationMs: Long) {
    val freezeWatcher = PluginFreezeWatcher.getInstance()

    val freezeReason = freezeWatcher.getFreezeReason()
    if (freezeReason != null) return // still have previous reason shown to user

    for (dump in currentDumps) {
      val reason = freezeWatcher.dumpedThreads(event, dump, durationMs)
      if (reason != null) {
        LifecycleUsageTriggerCollector.pluginFreezeDetected(reason.pluginId, durationMs)

        reportFreeze()
        break
      }
    }
  }

  private fun reportFreeze() {
    for (project in ProjectManager.getInstance().openProjects) {
      EditorNotifications.getInstance(project).updateAllNotifications()
    }
  }
}

internal class PluginFreezeNotificationPanel : EditorNotificationProvider {
  private val reported: MutableSet<FreezeReason> = Collections.synchronizedSet(HashSet())

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!Registry.get("ide.diagnostics.notification.freezes.in.plugins").asBoolean()) return null

    val freezeReason = PluginFreezeWatcher.getInstance().getFreezeReason() ?: return null
    val frozenPlugin = freezeReason.pluginId
    val pluginDescriptor = PluginManagerCore.getPlugin(frozenPlugin) ?: return null

    return Function {
      EditorNotificationPanel(EditorNotificationPanel.Status.Warning).apply {
        text = if (isVendorJetBrains(pluginDescriptor.vendor ?: "")) {
          PluginFreezeBundle.message("notification.content.freeze.detected", ApplicationInfoImpl.getShadowInstance().versionName)
        }
        else {
          PluginFreezeBundle.message("notification.content.plugin.caused.freeze", pluginDescriptor.name)
        }

        createActionLabel(PluginFreezeBundle.message("action.report.text")) {
          reportFreeze(project, pluginDescriptor, freezeReason)
        }
        createActionLabel(PluginFreezeBundle.message("action.ignore.plugin.text")) {
          PluginsFreezesService.getInstance().mutePlugin(frozenPlugin)

          LifecycleUsageTriggerCollector.pluginFreezeIgnored(frozenPlugin)

          closePanel(project)
        }.apply {
          toolTipText = PluginFreezeBundle.message("action.ignore.plugin.tooltip")
        }

        createActionLabel(PluginFreezeBundle.message("action.close.panel.text")) {
          closePanel(project)
        }.apply {
          toolTipText = PluginFreezeBundle.message("action.dismiss.tooltip")
        }
      }
    }
  }

  private fun reportFreeze(project: Project, pluginDescriptor: PluginDescriptor, freezeReason: FreezeReason) {
    if (reported.add(freezeReason)) {
      // must be added only once
      MessagePool.getInstance().addIdeFatalMessage(freezeReason.event)
    }

    val dialog = object : IdeErrorsDialog(MessagePool.getInstance(), project, null) {
      override fun updateOnSubmit() {
        super.updateOnSubmit()

        PluginsFreezesService.getInstance().mutePlugin(pluginDescriptor.pluginId)

        LifecycleUsageTriggerCollector.pluginFreezeReported(pluginDescriptor.pluginId)
        closePanel(project)
      }
    }

    dialog.show()
  }

  private fun closePanel(project: Project) {
    PluginFreezeWatcher.getInstance().reset()
    reported.clear()
    EditorNotifications.getInstance(project).updateAllNotifications()
  }
}