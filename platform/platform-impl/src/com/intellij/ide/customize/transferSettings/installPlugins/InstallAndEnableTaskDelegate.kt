// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.installPlugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiserDialogPluginInstaller
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.getInstallAndEnableTask
import java.util.function.Consumer

class InstallAndEnableTaskDelegate(project: Project?,
                                   private val pluginIds: Set<PluginId>,
                                   showDialog: Boolean,
                                   selectAllInDialog: Boolean,
                                   modalityState: ModalityState,
                                   private val parentProgressIndicator: ProgressIndicator,
                                   private val myOnSuccess: () -> Unit,
) : Task.Backgroundable(project, IdeBundle.message("plugins.advertiser.task.searching.for.plugins"), true) {
  private val delegating = getInstallAndEnableTask(project, pluginIds, showDialog, selectAllInDialog, modalityState, myOnSuccess)

  override fun run(indicator: ProgressIndicator) {
    delegating.run(indicator)

    val cp = delegating.customPlugins ?: return
    val a = object : PluginsAdvertiserDialogPluginInstaller(myProject, delegating.plugins, cp, ::runOnSuccess) {
      override fun downloadPlugins(plugins: MutableList<PluginNode>,
                                   customPlugins: MutableCollection<PluginNode>,
                                   onSuccess: Runnable?,
                                   modalityState: ModalityState,
                                   function: Consumer<in Boolean>?): Boolean {
        TransferSettingsDownloadPluginsTask(project, plugins, customPlugins,
                                            true, PluginEnabler.HEADLESS, ModalityState.any(), function).run(indicator)
        return true
      }
    }
    a.doInstallPlugins({ true }, ModalityState.any())
  }

  private fun runOnSuccess(onSuccess: Boolean) {
    if (onSuccess) {
      myOnSuccess()
    }
  }
}