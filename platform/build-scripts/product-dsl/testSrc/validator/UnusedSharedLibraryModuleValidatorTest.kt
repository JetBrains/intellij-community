// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.REQUIRED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.UnusedSharedLibraryModuleError
import org.jetbrains.intellij.build.productLayout.traversal.analyzeUnusedSharedLibraryModules
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class UnusedSharedLibraryModuleValidatorTest {
  @Test
  fun `keeps a shared library that platform content depends on`() {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") {
        module("intellij.platform.core", EMBEDDED)
        module("intellij.libraries.shared", REQUIRED)
      }
      linkContentModuleDeps("intellij.platform.core", "intellij.libraries.shared")
    }

    assertThat(analyzeUnusedSharedLibraryModules(graph).violations).isEmpty()
  }

  @Test
  fun `ignores embedded library modules`() {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") { module("intellij.libraries.embedded", EMBEDDED) }
    }

    // embedded libraries are UnusedEmbeddedLibraryModuleValidator's business
    assertThat(analyzeUnusedSharedLibraryModules(graph).violations).isEmpty()
  }

  @Test
  fun `counts a plugin module dependency as a consumer`() {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("core")
        bundlesPlugin("consumer.plugin")
      }
      moduleSet("core") { module("intellij.libraries.shared", REQUIRED) }
      plugin("consumer.plugin") { dependsOnContentModule("intellij.libraries.shared") }
    }

    assertThat(analyzeUnusedSharedLibraryModules(graph).violations).isEmpty()
  }

  @Test
  fun `reports a shared library nothing depends on`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") {
        module("intellij.platform.core", EMBEDDED)
        module("intellij.libraries.dead", REQUIRED)
      }
    }

    val errors = runValidationRule(UnusedSharedLibraryModuleValidator, testGenerationModel(graph))

    assertThat(errors).hasSize(1)
    val violation = (errors.single() as UnusedSharedLibraryModuleError).violations.single()
    assertThat(violation.module).isEqualTo("intellij.libraries.dead")
    assertThat(violation.declaringModuleSets).containsExactly("core")
    assertThat(violation.availableProducts).containsExactly("IDEA")
  }

  @Test
  fun `does not accept a library wrapper as justification for another library`() {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") {
        module("intellij.libraries.first", REQUIRED)
        module("intellij.libraries.second", REQUIRED)
      }
      linkContentModuleDeps("intellij.libraries.first", "intellij.libraries.second")
    }

    assertThat(analyzeUnusedSharedLibraryModules(graph).violations.map { it.module })
      .containsExactly("intellij.libraries.first", "intellij.libraries.second")
  }

  @Test
  fun `reports over-shipping as a diagnostic rather than a violation`() {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("core")
        bundlesPlugin("consumer.plugin")
      }
      product("CodeServer") { includesModuleSet("core") }
      moduleSet("core") { module("intellij.libraries.shared", REQUIRED) }
      plugin("consumer.plugin") { dependsOnContentModule("intellij.libraries.shared") }
    }

    val result = analyzeUnusedSharedLibraryModules(graph)

    assertThat(result.violations).isEmpty()
    val overShipped = result.overShipped.single()
    assertThat(overShipped.module).isEqualTo("intellij.libraries.shared")
    assertThat(overShipped.productsWithoutConsumer).containsExactly("CodeServer")
    assertThat(overShipped.pluginConsumers).containsExactly("consumer.plugin")
  }
}
