/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.externalSystem.service.project.settings

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.ObjectUtils.consumeIfCast

/**
 * Created by Nikita.Skvortsov
 */
class RunConfigurationHandler : ConfigurationHandler {

  companion object {
    val LOG = Logger.getInstance(RunConfigurationHandler::class.java)
  }

  private fun Any?.isTrue(): Boolean = this != null && this is Boolean && this

  override fun apply(module: Module, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {}

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val runCfgMap = configuration.find("runConfigurations")
    val runManagerEx = RunManagerEx.getInstanceEx(project)

    if (runCfgMap !is List<*>) return

    runCfgMap
      .filterIsInstance<Map<*, *>>()
      .sortedByDescending { it["defaults"].isTrue() }
      .forEach { cfg ->

        val name = cfg["name"] as? String ?: ""
        val typeName = cfg["type"] as? String
        if (typeName == null) {
          LOG.warn("Missing type for run configuration: '${name}', skipping")
          return@forEach
        }

        val importer = RunConfigImporterExtensionManager.handlerForType(typeName)
        if (importer == null) {
          LOG.warn("No importers for run configuration '${name}' with type '$typeName', skipping")
          return@forEach
        }

        val isDefaults = cfg["defaults"].isTrue()

        val runnerAndConfigurationSettings = if (isDefaults) {
          runManagerEx.getConfigurationTemplate(importer.configurationFactory)
        }
        else {
          runManagerEx.createConfiguration(name, importer.configurationFactory)
        }

        try {
          importer.process(project, runnerAndConfigurationSettings.configuration, cfg as Map<String, *>, modelsProvider)
          if (!isDefaults) {
            runManagerEx.addConfiguration(runnerAndConfigurationSettings)
          }

          consumeIfCast(cfg["beforeRun"], List::class.java) {
            // TODO add extension point
            it.filterIsInstance(Map::class.java)
              .firstOrNull { it["id"] == "Make" }
              ?.let { cfg ->
                val enabled = (cfg["enabled"] as? Boolean) ?: true
                if (!enabled) {
                  val filtered = runManagerEx
                    .getBeforeRunTasks(runnerAndConfigurationSettings.configuration)
                    .filterNot { it.providerId == CompileStepBeforeRun.ID }
                  runManagerEx.setBeforeRunTasks(runnerAndConfigurationSettings.configuration, filtered)
                }
              }
          }
        }
        catch (e: Exception) {
          LOG.warn("Error occurred when importing run configuration ${name}: ${e.message}", e)
        }
      }
  }
}


class RunConfigImporterExtensionManager {
  companion object {
    fun handlerForType(typeName: String): RunConfigurationImporter? =
      Extensions.getExtensions(
        RunConfigurationImporter.EP_NAME).firstOrNull { it.canImport(typeName) }
  }
}

