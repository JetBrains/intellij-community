// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import java.nio.file.Path
import kotlin.io.path.name

@ConsistentCopyVisibility
data class CompoundRunConfigurationProperties private constructor(@JvmField val name: String, @JvmField val toRun: List<String>) {
  companion object {
    internal const val TYPE = "CompoundRunConfigurationType"

    fun loadRunConfiguration(file: Path): CompoundRunConfigurationProperties {
      val configuration = getRunConfiguration(file)
      if (!configuration.getAttributeValue("type").equals(TYPE)) {
        throw RuntimeException("Cannot load compound configuration from '${file.name}': ${TYPE} run configuration type is expected")
      }

      val configurationName = requireNotNull(configuration.getAttributeValue("name")) { "Missing run configuration name in $file" }
      val toRun = mutableListOf<String>()
      for (child in configuration.children) {
        if (child.name == "toRun") {
          val name = requireNotNull(child.getAttributeValue("name")) { "Expected 'name' attribute on every 'toRun' element: $file" }
          toRun.add(name)
        }
      }

      check(toRun.isNotEmpty()) {
        "Empty list of run configurations to run in $file"
      }

      return CompoundRunConfigurationProperties(name = configurationName, toRun = toRun)
    }
  }
}