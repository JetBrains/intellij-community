// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers

import com.intellij.ide.IdeBundle
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.util.application

open class TransferSettingsPerformImportTask(
  project: Project?,
  private val performer: ImportPerformer,
  private var settings: Settings,
  private val shouldInstallPlugins: Boolean,
  private val context: TransferSettingsPerformContext? = null
) : Task.Backgroundable(
  project,
  IdeBundle.message("transfersettings.task.progress.title.importing.settings"),
  false
) {
  override fun run(indicator: ProgressIndicator) {
    indicator.isIndeterminate = true
    indicator.text2 = IdeBundle.message("transfersettings.task.progress.details.starting.up")
    indicator.checkCanceled()
    val requiredPlugins = performer.collectAllRequiredPlugins(settings)
    indicator.isIndeterminate = false
    indicator.fraction = 0.0

    if (shouldInstallPlugins) {
      runBlockingCancellable {
        val installationResult = performer.installPlugins(project, requiredPlugins, indicator)
        context?.pluginInstallationState = installationResult
      }
    }

    indicator.checkCanceled()
    settings = performer.patchSettingsAfterPluginInstallation(settings, PluginManagerCore.plugins.map { it.pluginId.idString }.toSet())

    indicator.checkCanceled()
    performer.perform(project, settings, indicator)
    indicator.fraction = 0.99
    indicator.text = IdeBundle.message("transfersettings.task.progress.details.finishing.up")
    indicator.text2 = null
    indicator.checkCanceled()
    application.invokeAndWait({ performer.performEdt(project, settings) }, indicator.modalityState)

    indicator.fraction = 1.0
    indicator.text = IdeBundle.message("transfersettings.task.progress.details.complete")
  }
}

data class TransferSettingsPerformContext(var pluginInstallationState: PluginInstallationState = PluginInstallationState.NoPlugins)
