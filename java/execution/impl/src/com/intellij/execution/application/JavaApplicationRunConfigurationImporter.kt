/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.application

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter
import com.intellij.openapi.project.Project

class JavaApplicationRunConfigurationImporter : RunConfigurationImporter {
  override fun process(project: Project, runConfiguration: RunConfiguration, cfg: Map<String, *>, modelsProvider: IdeModifiableModelsProvider) {
    if (runConfiguration !is ApplicationConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfiguration::class.java}")
    }

    (cfg["moduleName"] as? String)
      ?.let { modelsProvider.modifiableModuleModel.findModuleByName(it) }
      ?.let { runConfiguration.setModule(it) }

    (cfg["mainClass"] as? String)?.let { runConfiguration.mainClassName = it }
    (cfg["jvmArgs"]   as? String)?.let { runConfiguration.vmParameters = it  }
    (cfg["programParameters"] as? String)?.let { runConfiguration.programParameters = it }
    (cfg["envs"] as? Map<*,*>)?.let { runConfiguration.envs = it as MutableMap<String, String> }
  }

  override fun canImport(typeName: String): Boolean = typeName == "application"

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil.findConfigurationType<ApplicationConfigurationType>(
      ApplicationConfigurationType::class.java)
      .configurationFactories[0]
}