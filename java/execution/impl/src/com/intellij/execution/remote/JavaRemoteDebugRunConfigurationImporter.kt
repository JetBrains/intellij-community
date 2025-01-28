/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.remote

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter
import com.intellij.openapi.project.Project

class JavaRemoteDebugRunConfigurationImporter : RunConfigurationImporter {
  override fun process(project: Project, runConfiguration: RunConfiguration, cfg: Map<String, *>, modelsProvider: IdeModifiableModelsProvider) {
    if (runConfiguration !is RemoteConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfiguration::class.java}")
    }

    (cfg["moduleName"] as? String)?.let {
        val module = modelsProvider.modifiableModuleModel.findModuleByName(it)
        if (module != null) {
          runConfiguration.setModule(module)
        }
      }

    with(runConfiguration) {
      USE_SOCKET_TRANSPORT = (cfg["transport"] as? String) != "SHARED_MEM"
      SERVER_MODE = (cfg["mode"] as? String) == "LISTEN"
      PORT = (cfg["port"] as? Number)?.toInt()?.toString()
      HOST = cfg["host"] as? String
      SHMEM_ADDRESS = cfg["sharedMemoryAddress"] as? String
      AUTO_RESTART = (cfg["autoRestart"] as? Boolean) ?: false
    }
  }

  override fun canImport(typeName: String): Boolean = typeName == "remote"

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil.findConfigurationType(
      RemoteConfigurationType::class.java)
      .configurationFactories[0]
}