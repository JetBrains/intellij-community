// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.junit.jupiter.api.Assertions
import java.nio.file.Path

/**
 * Checks that runtime module descriptors in the product distribution are valid.
 */
class RuntimeModuleRepositoryChecker(private val distAllDir: Path) {
  fun check() {
    val jarFile = distAllDir.resolve(RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME)
    val descriptors = RuntimeModuleRepositorySerialization.loadFromJar(jarFile)
    val errors = ArrayList<String>()
    descriptors.values.forEach { descriptor -> 
      descriptor.dependencies.forEach { dependency ->
        if (dependency !in descriptors) {
          errors.add("Unknown dependency '$dependency' in module '${descriptor.id}'")
        }
      }
    }
    if (errors.isNotEmpty()) {
      return Assertions.fail("Runtime module repository has problems:\n" +
                             errors.joinToString("\n"))
    }
  }
}