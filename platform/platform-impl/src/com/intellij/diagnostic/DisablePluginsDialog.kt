// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.nio.file.FileVisitResult

internal object DisablePluginsDialog {
  @JvmStatic
  internal fun confirmDisablePlugins(project: Project?, pluginsToDisable: List<IdeaPluginDescriptor>) {
    if (pluginsToDisable.isEmpty()) {
      return
    }
    val pluginIdsToDisable = pluginsToDisable.mapTo(HashSet()) { obj: IdeaPluginDescriptor -> obj.pluginId }
    val hasDependents = morePluginsAffected(pluginIdsToDisable)
    val canRestart = ApplicationManager.getApplication().isRestartCapable
    val message =
      "<html>" +
      if (pluginsToDisable.size == 1) {
        val plugin = pluginsToDisable.iterator().next()
        DiagnosticBundle.message("error.dialog.disable.prompt", plugin.name) + "<br/>" +
        DiagnosticBundle.message(
          if (hasDependents) "error.dialog.disable.prompt.deps"
          else "error.dialog.disable.prompt.lone"
        )
      }
      else {
        DiagnosticBundle.message("error.dialog.disable.prompt.multiple") + "<br/>" +
        DiagnosticBundle.message(
          if (hasDependents) "error.dialog.disable.prompt.deps.multiple"
          else "error.dialog.disable.prompt.lone.multiple"
        )
      } + "<br/><br/>" +
      DiagnosticBundle.message(
        if (canRestart) "error.dialog.disable.plugin.can.restart"
        else "error.dialog.disable.plugin.no.restart"
      ) +
      "</html>"
    val title = DiagnosticBundle.message("error.dialog.disable.plugin.title")
    val disable = DiagnosticBundle.message("error.dialog.disable.plugin.action.disable")
    val cancel = IdeBundle.message("button.cancel")
    val (doDisable, doRestart) = if (canRestart) {
      val restart = DiagnosticBundle.message("error.dialog.disable.plugin.action.disableAndRestart")
      val result = Messages.showYesNoCancelDialog(project, message, title, disable, restart, cancel, Messages.getQuestionIcon())
      (result == Messages.YES || result == Messages.NO) to (result == Messages.NO)
    }
    else {
      val result = Messages.showYesNoDialog(project, message, title, disable, cancel, Messages.getQuestionIcon())
      (result == Messages.YES) to false
    }
    if (doDisable) {
      PluginEnabler.HEADLESS.disable(pluginsToDisable)
      if (doRestart) {
        ApplicationManager.getApplication().restart()
      }
    }
  }

  @JvmStatic
  private fun morePluginsAffected(pluginIdsToDisable: Set<PluginId>): Boolean {
    val pluginIdMap = PluginManagerCore.buildPluginIdMap()
    for (rootDescriptor in PluginManagerCore.plugins) {
      if (!rootDescriptor.isEnabled || pluginIdsToDisable.contains(rootDescriptor.pluginId)) {
        continue
      }
      if (!PluginManagerCore.processAllNonOptionalDependencies((rootDescriptor as IdeaPluginDescriptorImpl), pluginIdMap) { descriptor ->
          when {
            descriptor.isEnabled -> if (pluginIdsToDisable.contains(descriptor.pluginId)) FileVisitResult.TERMINATE
            else FileVisitResult.CONTINUE
            else -> FileVisitResult.SKIP_SUBTREE
          }
        } /* no need to process its dependencies */
      ) {
        return true
      }
    }
    return false
  }
}
