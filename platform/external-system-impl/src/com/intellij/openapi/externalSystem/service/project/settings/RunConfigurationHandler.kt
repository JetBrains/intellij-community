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

  private fun Any?.isTrue(): Boolean = this != null && this is String && this.toBoolean()

  override fun apply(module: Module, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    configuration.eachRunConfiguration(RunManager.getInstance(module.project),
                                       { handler, runConfiguration, externalCfg ->
                                         handler.process(module, runConfiguration, externalCfg)
                                       })
  }

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    configuration.eachRunConfiguration(RunManager.getInstance(project),
                                       { handler, runConfiguration, externalCfg ->
                                         handler.process(project, runConfiguration, externalCfg)
                                       })
  }

  private fun ConfigurationData.eachRunConfiguration(runManager: RunManager, visit: (RunConfigurationImporter, RunConfiguration, Map<String, *>) -> Unit) {
    val runCfgMap = find("runConfigurations")

    if (runCfgMap !is Map<*,*>) return

    runCfgMap.forEach { name, cfg ->
      if (name !is String) {
        LOG.warn("unexpected key type in runConfigurations map: ${name?.javaClass?.name}, skipping")
        return@forEach
      }

      if (cfg !is Map<*, *>) {
        LOG.warn("unexpected value type in runConfigurations map: ${cfg?.javaClass?.name}, skipping")
        return@forEach
      }

      val typeName = cfg["type"] as? String
      if (typeName == null) {
        LOG.warn("Missing type for run configuration: '${name}', skipping")
        return@forEach
      }

      val handler = RunConfigHandlerExtensionManager.handlerForType(typeName)
      if (handler == null) {
        LOG.warn("No importers for run configuration '${name}' with type '$typeName', skipping")
        return@forEach
      }

      val isDefaults = cfg["defaults"].isTrue()

      val runnerAndConfigurationSettings = if (isDefaults) {
        runManager.getConfigurationTemplate(handler.configurationFactory)
      }
      else {
        runManager.createConfiguration(name, handler.configurationFactory)
      }

      try {
        visit(handler, runnerAndConfigurationSettings.configuration, cfg as Map<String, *>)

        if (!isDefaults) {
          runManager.addConfiguration(runnerAndConfigurationSettings)
        }
      } catch (e: Exception) {
        LOG.warn("Error occurred when importing run configuration ${name}: ${e.message}", e)
      }
    }
  }
}


class RunConfigHandlerExtensionManager {
  companion object {
    fun handlerForType(typeName: String): RunConfigurationImporter? =
      Extensions.getExtensions(
        RunConfigurationImporter.EP_NAME).firstOrNull { it.canHandle(typeName) }
  }
}

class ApplicationRunConfigurationImporter : RunConfigurationImporter {
  override fun process(module: Module, runConfiguration: RunConfiguration, cfg: Map<String, *>) {
    if (runConfiguration !is ApplicationConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfiguration::class.java}")
    }

    runConfiguration.mainClassName = cfg["mainClass"] as? String
    runConfiguration.vmParameters = cfg["jvmArgs"] as? String
    runConfiguration.setModule(module)
  }

  override fun process(project: Project, runConfiguration: RunConfiguration, cfg: Map<String, *>) {}

  override fun canHandle(typeName: String): Boolean = typeName == "application"

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil
      .findConfigurationType<ApplicationConfigurationType>(ApplicationConfigurationType::class.java)
      .configurationFactories[0]
}