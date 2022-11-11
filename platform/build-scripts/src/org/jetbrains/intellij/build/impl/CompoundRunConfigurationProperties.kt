// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import java.nio.file.Path
import kotlin.io.path.name

data class CompoundRunConfigurationProperties(val name: String, val toRun: List<String>) {
  companion object {
    const val TYPE = "CompoundRunConfigurationType"

    fun loadRunConfiguration(file: Path): CompoundRunConfigurationProperties {
      val configuration = RunConfigurationProperties.getConfiguration(file)
      if (!configuration.getAttributeValue("type").equals(TYPE)) {
        throw RuntimeException("Cannot load compound configuration from \'${file.name}\': ${TYPE} run configuration type is expected")
      }

      val configurationName = configuration.getAttributeValue("name")
                              ?: error("Missing run configuration name in $file")

      val toRun = mutableListOf<String>()
      for (child in configuration.children) {
        if (child.name == "toRun") {
          val name = child.getAttributeValue("name")
                     ?: error("Expected 'name' attribute on every 'toRun' element: $file")
          toRun.add(name)
        }
      }

      if (toRun.isEmpty()) {
        error("Empty list of run configurations to run in $file")
      }

      return CompoundRunConfigurationProperties(name = configurationName, toRun = toRun)
    }
  }
}