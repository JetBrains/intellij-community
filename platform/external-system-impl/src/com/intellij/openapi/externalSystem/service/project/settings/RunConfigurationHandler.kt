/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.externalSystem.service.project.settings

import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
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

  override fun apply(module: Module, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    configuration.eachRunConfiguration { typeName, name, cfg ->
      RunConfigHandlerExtensionManager.handlerForType(
        typeName)?.process(module, name, cfg )
    }
  }

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    configuration.eachRunConfiguration { typeName, name, cfg ->
      RunConfigHandlerExtensionManager.handlerForType(
        typeName)?.process(project, name, cfg)
    }
  }

  private fun ConfigurationData.eachRunConfiguration(f: (String, String, Map<String, *>) -> Unit) {
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
        LOG.warn("Missing type for run configuration: ${name}, skipping")
        return@forEach
      }

      try {
        f(typeName, name, cfg as Map<String, *>)
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
  override fun process(module: Module, name: String, cfg: Map<String, *>) {
    val cfgType = ConfigurationTypeUtil.findConfigurationType<ApplicationConfigurationType>(
      ApplicationConfigurationType::class.java)
    val runManager = RunManager.getInstance(module.project)
    val runnerAndConfigurationSettings = runManager.createConfiguration(name, cfgType.configurationFactories[0])
    val appConfig = runnerAndConfigurationSettings.configuration as ApplicationConfiguration

    appConfig.setMainClassName(cfg["mainClass"] as? String)
    appConfig.vmParameters = cfg["jvmArgs"] as? String
    appConfig.setModule(module)

    runManager.addConfiguration(runnerAndConfigurationSettings)
  }

  override fun process(project: Project, name: String, cfg: Map<String, *>) {}

  override fun canHandle(typeName: String): Boolean = typeName == "application"
}