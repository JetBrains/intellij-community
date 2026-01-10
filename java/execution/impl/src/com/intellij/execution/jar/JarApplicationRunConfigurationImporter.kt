// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jar

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter
import com.intellij.openapi.project.Project

class JarApplicationRunConfigurationImporter : RunConfigurationImporter {
  @Suppress("UNCHECKED_CAST")
  override fun process(
    project: Project,
    runConfiguration: RunConfiguration,
    cfg: MutableMap<String, Any>,
    modelsProvider: IdeModifiableModelsProvider,
  ) {
    if (runConfiguration !is JarApplicationConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfiguration::class.java}")
    }
    val moduleName = cfg["moduleName"] as? String
    val module = moduleName?.let(modelsProvider.modifiableModuleModel::findModuleByName)

    if (module != null) {
      runConfiguration.module = module
    }
    val jrePath = (cfg["alternativeJrePath"] as? String)?.takeIf { it.isNotEmpty() }
    with(runConfiguration) {
      jarPath = cfg["jarPath"] as? String ?: ""
      vmParameters = cfg["jvmArgs"] as? String ?: ""
      programParameters = cfg["programParameters"] as? String ?: ""
      workingDirectory = cfg["workingDirectory"] as? String ?: ""
      envs = cfg["envs"] as? Map<String, String> ?: emptyMap()
      isAlternativeJrePathEnabled = jrePath != null
      alternativeJrePath = jrePath
    }
  }

  override fun canImport(typeName: String): Boolean = typeName == "jarApplication"

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil.findConfigurationType(
      JarApplicationConfigurationType::class.java
    ).configurationFactories[0]
}