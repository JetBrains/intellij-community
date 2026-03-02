// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginTestSetup
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.moduleSet
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContextImpl
import org.jetbrains.intellij.build.productLayout.pipeline.DiscoveryResult
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class ProductModuleDependencyGeneratorTest {
  @Test
  fun `product module written deps exclude self dependency`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val moduleName = "intellij.self.module"
      val setup = pluginTestSetup(tempDir) {
        contentModule(moduleName) {
          descriptor = """<idea-plugin package="self.module"/>"""
          jpsDependency(moduleName, JpsJavaDependencyScope.COMPILE)
        }
      }

      val model = testGenerationModel(
        pluginGraph = setup.pluginGraph,
        outputProvider = setup.jps.outputProvider,
        fileUpdater = setup.strategy,
      ).copy(
        discovery = DiscoveryResult(
          moduleSetsByLabel = mapOf(
            "community" to listOf(
              moduleSet("self.deps.test", includeDependencies = true) {
                module(moduleName)
              }
            )
          ),
          products = emptyList(),
          testProductSpecs = emptyList(),
          moduleSetSources = emptyMap(),
        )
      )

      val ctx = ComputeContextImpl(model)
      ctx.initSlot(Slots.PRODUCT_MODULE_DEPS)
      val nodeCtx = ctx.forNode(ProductModuleDependencyGenerator.id)
      ProductModuleDependencyGenerator.execute(nodeCtx)
      ctx.finalizeNodeErrors(ProductModuleDependencyGenerator.id)

      val output = ctx.get(Slots.PRODUCT_MODULE_DEPS)
      val moduleResult = output.files.single { it.contentModuleName == ContentModuleName(moduleName) }
      assertThat(moduleResult.writtenDependencies)
        .describedAs("Self dependencies should not be written for product modules")
        .doesNotContain(ContentModuleName(moduleName))
    }
  }
}
