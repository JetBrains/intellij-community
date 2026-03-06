/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.application

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter
import com.intellij.openapi.project.Project
import com.intellij.util.ObjectUtils.consumeIfCast

class JavaApplicationRunConfigurationImporter : RunConfigurationImporter {
  @Suppress("UNCHECKED_CAST")
  override fun process(
    project: Project,
    runConfiguration: RunConfiguration,
    cfg: Map<String, *>,
    modelsProvider: IdeModifiableModelsProvider,
  ) {
    if (runConfiguration !is ApplicationConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfiguration::class.java}")
    }

    val moduleName = cfg["moduleName"] as? String
    val module = moduleName?.let(modelsProvider.modifiableModuleModel::findModuleByName)
    if (module != null) {
      runConfiguration.setModule(module)
    }

    val jrePath = (cfg["alternativeJrePath"] as? String)?.takeIf { it.isNotEmpty() }
    val shortenCmdLine = cfg["shortenCommandLine"] as? String
    with(runConfiguration) {
      mainClassName = cfg["mainClass"] as? String
      vmParameters = cfg["jvmArgs"] as? String
      programParameters = cfg["programParameters"] as? String
      envs = cfg["envs"] as? MutableMap<String, String> ?: mutableMapOf()
      workingDirectory = cfg["workingDirectory"] as? String
      setIncludeProvidedScope(cfg["includeProvidedDependencies"] as? Boolean ?: false)
      isAlternativeJrePathEnabled = jrePath != null
      alternativeJrePath = jrePath

      if (shortenCmdLine != null) {
        try {
          shortenCommandLine = ShortenCommandLine.valueOf(shortenCmdLine)
        }
        catch (e: IllegalArgumentException) {
          LOG.warn("Illegal value of 'shortenCommandLine': $shortenCmdLine", e)
        }
      }
    }
  }

  override fun canImport(typeName: String): Boolean = typeName == "application"

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil.findConfigurationType(
      ApplicationConfigurationType::class.java)
      .configurationFactories[0]

  companion object {
    val LOG: Logger = Logger.getInstance(JavaApplicationRunConfigurationImporter::class.java)
  }
}