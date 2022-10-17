// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers

import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.application

open class TransferSettingsPerformImportTask(project: Project,
                                             private val performer: ImportPerformer,
                                             private var settings: Settings,
                                             private val shouldInstallPlugins: Boolean) : Task.Backgroundable(project, "Importing settings",
                                                                                                              false) {
  override fun run(indicator: ProgressIndicator) {
    indicator.isIndeterminate = true
    indicator.text2 = "Starting up..."
    val requiredPlugins = performer.collectAllRequiredPlugins(settings)
    indicator.isIndeterminate = false
    indicator.fraction = 0.0

    if (shouldInstallPlugins) {
      performer.installPlugins(project, requiredPlugins, indicator)
    }

    settings = performer.patchSettingsAfterPluginInstallation(settings, PluginManagerCore.getPlugins().map { it.pluginId.idString }.toSet())

    performer.perform(project, settings, indicator)
    indicator.isIndeterminate = true
    indicator.text2 = "Finishing up..."
    application.invokeAndWait({ performer.performEdt(project, settings) }, indicator.modalityState)

    indicator.text2 = "Complete"
  }
}