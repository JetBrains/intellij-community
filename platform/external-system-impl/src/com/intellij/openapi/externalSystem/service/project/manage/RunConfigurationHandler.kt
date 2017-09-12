/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.externalSystem.model.project.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Created by Nikita.Skvortsov
 * date: 05.09.2017.
 */

class RunConfigurationHandler: ConfigurationHandler {

  companion object {
    val LOG = Logger.getInstance(RunConfigurationHandler::class.java)
  }

  override fun apply(module: Module, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    configuration.eachRunConfiguration { typeName, name, cfg ->
      RunConfigHandlerExtensionManager.handlerForType(typeName)?.process(module, name, cfg )
    }
  }

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    configuration.eachRunConfiguration { typeName, name, cfg ->
      RunConfigHandlerExtensionManager.handlerForType(typeName)?.process(project, name, cfg)
    }
  }

  private fun ConfigurationData.eachRunConfiguration(f: (String, String, Map<String, String>) -> Unit) {
    val runCfgMap = find("runConfigurations")

    if (runCfgMap !is Map<*,*>) return

    runCfgMap.forEach { name, cfg ->
      if (name !is String) {
        RunConfigurationHandler.LOG.warn("unexpected key type in runConfigurations map: ${name?.javaClass?.name}, skipping")
        return@forEach
      }
      if (cfg !is Map<*, *>) {
        RunConfigurationHandler.LOG.warn("unexpected value type in runConfigurations map: ${cfg?.javaClass?.name}, skipping")
        return@forEach
      }

      val typeName = cfg["type"] as? String

      if (typeName == null) {
        RunConfigurationHandler.LOG.warn("Missing type for run configuration: ${name}, skipping")
        return@forEach
      }

      try {
        f(typeName, name, cfg as Map<String, String>)
      } catch (e: Exception) {
        RunConfigurationHandler.LOG.warn("Error occurred when importing run configuration ${name}: ${e.message}", e)
      }
    }
  }
}


class RunConfigHandlerExtensionManager {
  companion object {
    fun handlerForType(typeName: String): RunConfigHandlerExtension? =
      Extensions.getExtensions(RunConfigHandlerExtension.EP_NAME).firstOrNull { it.canHandle(typeName) }
  }
}

class ApplicationRunConfigHandler: RunConfigHandlerExtension {
  override fun process(module: Module, name: String, cfg: Map<String, *>) {
    val cfgType = ConfigurationTypeUtil.findConfigurationType<ApplicationConfigurationType>(
      ApplicationConfigurationType::class.java)
    val runManager = RunManager.getInstance(module.project)
    val runnerAndConfigurationSettings = runManager.createConfiguration(name, cfgType.configurationFactories[0])
    val appConfig = runnerAndConfigurationSettings.configuration as ApplicationConfiguration

    appConfig.MAIN_CLASS_NAME = cfg["mainClass"] as? String
    appConfig.VM_PARAMETERS   = cfg["jvmArgs"] as? String
    appConfig.setModule(module)

    runManager.addConfiguration(runnerAndConfigurationSettings)
  }

  override fun process(project: Project, name: String, cfg: Map<String, *>) {}

  override fun canHandle(typeName: String): Boolean = typeName == "application"
}