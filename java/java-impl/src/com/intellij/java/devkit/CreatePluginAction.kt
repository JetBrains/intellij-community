// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.devkit

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.getInstallAndEnableTask
import com.intellij.util.PlatformUtils

private const val DEVKIT_PLUGIN_ID = "DevKit"
private const val GRAMMARKIT_PLUGIN_ID = "org.jetbrains.idea.grammar"

internal class CreatePluginAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = e.project != null && PlatformUtils.isIntelliJ() // not available in Android Studio
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (PluginManagerCore.isPluginInstalled(PluginId.getId(DEVKIT_PLUGIN_ID))) {
      proceedToWizard(e.dataContext)
      return
    }

    val plugins = setOf(
      PluginId.getId(DEVKIT_PLUGIN_ID),
      PluginId.getId(GRAMMARKIT_PLUGIN_ID)
    )

    val dataContext = e.dataContext
    ProgressManager.getInstance().run(
      getInstallAndEnableTask(null, plugins, true, true, null) {
        proceedToWizard(dataContext)
      }
    )
  }

  private fun proceedToWizard(d: DataContext) {
    val event = AnActionEvent.createEvent(d, Presentation(), "", ActionUiKind.NONE, null)
    ActionUtil.performAction(ActionManager.getInstance().getAction("NewProject"), event)
  }
}
