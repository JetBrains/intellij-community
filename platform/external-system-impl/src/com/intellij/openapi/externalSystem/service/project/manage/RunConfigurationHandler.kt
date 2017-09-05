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
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.externalSystem.model.project.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Created by Nikita.Skvortsov
 * date: 05.09.2017.
 */

class RunConfigurationHandler: ConfigurationHandler {

  override fun apply(module: Module, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val runCfgMap = configuration.find("runConfigurations")

    if (runCfgMap !is Map<*,*>) return

    runCfgMap.forEach { name, cfg ->
      if (name !is String) return@forEach // TODO logs
      if (cfg !is Map<*, *>) return@forEach
      if (cfg["type"] == null) return@forEach

      val runCfgType = cfg["type"] as? String ?: return@forEach
      RunConfigHandlerExtensionManager.handlerForType(runCfgType)?.process(module, name, cfg as Map<String, String>)
    }
  }

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val runCfgMap = configuration.find("runConfigurations")

    if (runCfgMap !is Map<*,*>) return

    runCfgMap.forEach { name, cfg ->
      if (name !is String) return@forEach // TODO logs
      if (cfg  !is Map<*,*>) return@forEach
      if (cfg["type"] == null) return@forEach

      val runCfgType = cfg["type"] as? String ?: return@forEach
      RunConfigHandlerExtensionManager.handlerForType(runCfgType)?.process(project, name, cfg as Map<String, String>)
    }
  }
}


@ApiStatus.Experimental
interface RunConfigHandlerExtension {
  companion object {
    val EP_NAME = ExtensionPointName.create<RunConfigHandlerExtension>("com.intellij.runConfigurationHandlerExtension")
  }

  fun process(project: Project, name: String, cfg: Map<String, String>) {}
  fun process(module: Module, name: String, cfg: Map<String, String>) {}
  fun canHandle(typeName: String): Boolean = false
}

class RunConfigHandlerExtensionManager {
  companion object {
    fun handlerForType(typeName: String): RunConfigHandlerExtension? =
      Extensions.getExtensions(RunConfigHandlerExtension.EP_NAME).firstOrNull { it.canHandle(typeName) }
  }
}

class ApplicationRunConfigHandler: RunConfigHandlerExtension {
  override fun process(module: Module, name: String, cfg: Map<String, String>) = process(module.project, name, cfg)

  override fun process(project: Project, name: String, cfg: Map<String, String>) {
    val cfgType = ConfigurationTypeUtil.findConfigurationType<ApplicationConfigurationType>(
      ApplicationConfigurationType::class.java)
    val runManager = RunManager.getInstance(project)
    val runnerAndConfigurationSettings = runManager.createConfiguration(name, cfgType.configurationFactories[0])
    val appConfig = runnerAndConfigurationSettings.configuration as ApplicationConfiguration

    appConfig.MAIN_CLASS_NAME = cfg["className"]
    appConfig.VM_PARAMETERS   = cfg["jvmArgs"]

    runManager.addConfiguration(runnerAndConfigurationSettings)
  }

  override fun canHandle(typeName: String): Boolean = typeName == "application"
}