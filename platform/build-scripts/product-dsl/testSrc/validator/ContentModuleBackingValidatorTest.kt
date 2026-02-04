// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.jpsProject
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.MissingContentModuleBackingError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class ContentModuleBackingValidatorTest {
  @Test
  fun `reports missing JPS module for declared content module`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("module.exists") {
        resourceRoot("resources")
      }
    }
    writeDescriptor(tempDir, "module.exists")

    val graph = pluginGraph {
      plugin("my.plugin") {
        content("module.exists")
        content("module.missing")
      }
      moduleWithDeps("module.exists")
      moduleWithDeps("module.missing")
    }

    val model = testGenerationModel(graph, outputProvider = jps.outputProvider)
    val errors = runBlocking { runValidationRule(ContentModuleBackingValidator, model) }

    assertThat(errors).hasSize(1)
    val error = errors[0] as MissingContentModuleBackingError
    assertThat(error.missingModules.keys).containsExactly(ContentModuleName("module.missing"))
  }

  @Test
  fun `passes when declared content module resolves to JPS module`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("module.exists") {
        resourceRoot("resources")
      }
    }
    writeDescriptor(tempDir, "module.exists")

    val graph = pluginGraph {
      plugin("my.plugin") {
        content("module.exists")
      }
      moduleWithDeps("module.exists")
    }

    val model = testGenerationModel(graph, outputProvider = jps.outputProvider)
    val errors = runBlocking { runValidationRule(ContentModuleBackingValidator, model) }

    assertThat(errors).isEmpty()
  }
}

@Suppress("SameParameterValue")
private fun writeDescriptor(tempDir: Path, moduleName: String) {
  val resourcesDir = tempDir.resolve(moduleName.replace('.', '/')).resolve("resources")
  Files.createDirectories(resourcesDir)
  Files.writeString(resourcesDir.resolve("$moduleName.xml"), "<idea-plugin/>")
}
