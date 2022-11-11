// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import java.nio.file.Path
import kotlin.io.path.name

class KotlinRunConfigurationProperties(
  name: String,
  moduleName: String,
  val mainClassName: String,
  val arguments: String,
  vmParameters: List<String>,
  envVariables: Map<String, String>
) : RunConfigurationProperties(name = name, moduleName = moduleName, vmParameters = vmParameters, envVariables = envVariables) {
  companion object {
    fun loadRunConfiguration(file: Path): KotlinRunConfigurationProperties {
      val configuration = getConfiguration(file)
      if (configuration.getAttributeValue("type") != "JetRunConfigurationType" &&
          configuration.getAttributeValue("factoryName") != "Kotlin") {
        throw RuntimeException("Cannot load configuration from \'" + file.name + "\': only Kotlin run configuration are supported")
      }

      val moduleName = getModuleName(configuration)
      val options = configuration.children("option").associate { it.getAttributeValue("name")!! to it.getAttributeValue("value") }
      return KotlinRunConfigurationProperties(name = configuration.getAttributeValue("name")!!,
                                              moduleName = moduleName,
                                              mainClassName = options.get("MAIN_CLASS_NAME")!!,
                                              arguments = options.get("PROGRAM_PARAMETERS")!!,
                                              vmParameters = getVmParameters(options),
                                              envVariables = getEnv(configuration))
    }
  }
}