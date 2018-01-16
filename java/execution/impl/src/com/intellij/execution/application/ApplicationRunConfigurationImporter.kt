/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.application

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter
import com.intellij.openapi.project.Project

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

    val runManager = RunManager.getInstance(project) as RunManagerEx

    (cfg["mainClass"] as? String)?.let { runConfiguration.mainClassName = it }
    (cfg["jvmArgs"]   as? String)?.let { runConfiguration.vmParameters = it  }
    (cfg["programParameters"] as? String)?.let { runConfiguration.programParameters = it }
    (cfg["envs"] as? Map<*,*>)?.let { runConfiguration.envs = it as MutableMap<String, String> }

    if (!isDefaults) {
      runConfiguration.setModule(module)
    }
  }

  override fun canImport(typeName: String): Boolean = typeName == "application"

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil.findConfigurationType<ApplicationConfigurationType>(
      ApplicationConfigurationType::class.java)
      .configurationFactories[0]
}