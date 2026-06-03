// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories

internal object RuntimeModuleRepositoryGenerator {
  const val JAR_REPOSITORY_FILE_NAME: String = "module-descriptors.jar"
  const val COMPACT_REPOSITORY_FILE_NAME: String = "module-descriptors.dat"
  const val GENERATOR_VERSION: Int = 3

  fun saveModuleRepository(descriptors: List<RawRuntimeModuleDescriptor>, pluginHeaders: List<RuntimePluginHeader>,
                           targetDirectory: Path) {
    try {
      val bootstrapModuleName = "intellij.platform.bootstrap"
      targetDirectory.createDirectories()
      RuntimeModuleRepositorySerialization.saveToCompactFile(descriptors,
                                                             pluginHeaders, bootstrapModuleName, targetDirectory.resolve(COMPACT_REPOSITORY_FILE_NAME), GENERATOR_VERSION)
      RuntimeModuleRepositorySerialization.saveToJar(descriptors,
                                                     pluginHeaders, bootstrapModuleName, targetDirectory.resolve(JAR_REPOSITORY_FILE_NAME), GENERATOR_VERSION)
    }
    catch (e: IOException) {
      throw RuntimeException("Failed to save runtime module repository: ${e.message}", e)
    }
  }
}
