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
import com.intellij.util.ObjectUtils.consumeIfCast

class JavaApplicationRunConfigurationImporter : RunConfigurationImporter {
  override fun process(project: Project, runConfiguration: RunConfiguration, cfg: Map<String, *>, modelsProvider: IdeModifiableModelsProvider) {
    if (runConfiguration !is ApplicationConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfiguration::class.java}")
    }

    consumeIfCast(cfg["moduleName"], String::class.java) {
        val module = modelsProvider.modifiableModuleModel.findModuleByName(it)
        if (module != null) {
          runConfiguration.setModule(module)
        }
      }

    consumeIfCast(cfg["mainClass"], String::class.java) { runConfiguration.mainClassName = it }
    consumeIfCast(cfg["jvmArgs"], String::class.java) { runConfiguration.vmParameters = it  }
    consumeIfCast(cfg["programParameters"], String::class.java) { runConfiguration.programParameters = it }
    consumeIfCast(cfg["envs"], Map::class.java) { runConfiguration.envs = it as MutableMap<String, String> }
  }

  override fun canImport(typeName: String): Boolean = typeName == "application"

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil.findConfigurationType<ApplicationConfigurationType>(
      ApplicationConfigurationType::class.java)
      .configurationFactories[0]
}