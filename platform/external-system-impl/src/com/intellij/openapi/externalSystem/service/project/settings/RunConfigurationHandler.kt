/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.externalSystem.service.project.settings

import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Created by Nikita.Skvortsov
 */
class RunConfigurationHandler: ConfigurationHandler {

  companion object {
    val LOG = Logger.getInstance(RunConfigurationHandler::class.java)
  }

  private fun Any?.isTrue(): Boolean = this != null && this is Boolean && this

  override fun apply(module: Module, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) { }

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    configuration.eachRunConfiguration(RunManager.getInstance(project),
                                       { importer, runConfiguration, externalCfg ->
                                         importer.process(project, runConfiguration, externalCfg, modelsProvider)
                                       })
  }

  private fun ConfigurationData.eachRunConfiguration(runManager: RunManager, visit: (RunConfigurationImporter, RunConfiguration, Map<String, *>) -> Unit) {
    val runCfgMap = find("runConfigurations")

    if (runCfgMap !is List<*>) return

    runCfgMap.sortedBy { !((it as? Map<*,*>)?.get("defaults").isTrue()) }.forEach { cfg ->
      if (cfg !is Map<*, *>) {
        LOG.warn("unexpected value type in runConfigurations map: ${cfg?.javaClass?.name}, skipping")
        return@forEach
      }

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
        runManager.getConfigurationTemplate(importer.configurationFactory)
      }
      else {
        runManager.createConfiguration(name, importer.configurationFactory)
      }

      try {
        visit(importer, runnerAndConfigurationSettings.configuration, cfg as Map<String, *>)

        if (!isDefaults) {
          runManager.addConfiguration(runnerAndConfigurationSettings)
        }
      } catch (e: Exception) {
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

class ApplicationRunConfigurationImporter : RunConfigurationImporter {
  override fun process(project: Project, runConfiguration: RunConfiguration, cfg: Map<String, *>, modelsProvider: IdeModifiableModelsProvider) {
    if (runConfiguration !is ApplicationConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfiguration::class.java}")
    }

    val isDefaults = (cfg["defaults"] as? Boolean) ?: false

    val module = (cfg["moduleName"] as? String)?.let { modelsProvider.modifiableModuleModel.findModuleByName(it) }
    if (module == null && !isDefaults) {
      throw IllegalArgumentException("Module with name ${cfg["moduleName"]} can not be found")
    }


    (cfg["mainClass"] as? String)?.let { runConfiguration.mainClassName = it }
    (cfg["jvmArgs"]   as? String)?.let { runConfiguration.vmParameters = it  }

    if (!isDefaults) {
      runConfiguration.setModule(module)
    }
  }

  override fun canImport(typeName: String): Boolean = typeName == "application"

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil
      .findConfigurationType<ApplicationConfigurationType>(ApplicationConfigurationType::class.java)
      .configurationFactories[0]
}