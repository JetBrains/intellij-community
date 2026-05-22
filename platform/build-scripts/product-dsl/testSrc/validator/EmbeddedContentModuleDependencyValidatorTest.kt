// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.OPTIONAL
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.REQUIRED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.EmbeddedContentModuleDependencyError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class EmbeddedContentModuleDependencyValidatorTest {
  @Test
  fun `reports embedded module dependency on bundled plugin content`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("core")
        bundlesPlugin("structure.plugin")
      }
      moduleSet("core") {
        module("core.embedded", EMBEDDED)
      }
      plugin("structure.plugin") {
        content("structure.impl", loading = OPTIONAL)
      }
      linkContentModuleDeps("core.embedded", "structure.impl")
    }

    val errors = runValidationRule(EmbeddedContentModuleDependencyValidator, testGenerationModel(graph))

    assertThat(errors).hasSize(1)
    val error = errors.single() as EmbeddedContentModuleDependencyError
    val violation = error.violations.single()
    assertThat(violation.sourceModule).isEqualTo("core.embedded")
    assertThat(violation.dependency).isEqualTo("structure.impl")
    assertThat(violation.dependencyPath).containsExactly("core.embedded", "structure.impl")
    assertThat(violation.dependencySources.map { it.kind to it.name }).containsExactly("plugin" to "structure.plugin")
  }

  @Test
  fun `does not report dependency on regular module set content`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("core")
      }
      moduleSet("core") {
        module("core.embedded", EMBEDDED)
        module("core.required", REQUIRED)
      }
      linkContentModuleDeps("core.embedded", "core.required")
    }

    val errors = runValidationRule(EmbeddedContentModuleDependencyValidator, testGenerationModel(graph))

    assertThat(errors).isEmpty()
  }

  @Test
  fun `does not report dependency on embedded module set content`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("core")
      }
      moduleSet("core") {
        module("core.embedded", EMBEDDED)
        module("core.embedded.dep", EMBEDDED)
      }
      linkContentModuleDeps("core.embedded", "core.embedded.dep")
    }

    val errors = runValidationRule(EmbeddedContentModuleDependencyValidator, testGenerationModel(graph))

    assertThat(errors).isEmpty()
  }

  @Test
  fun `does not report non-embedded source module dependency`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("core")
        bundlesPlugin("structure.plugin")
      }
      moduleSet("core") {
        module("core.required", REQUIRED)
      }
      plugin("structure.plugin") {
        content("structure.impl", loading = OPTIONAL)
      }
      linkContentModuleDeps("core.required", "structure.impl")
    }

    val errors = runValidationRule(EmbeddedContentModuleDependencyValidator, testGenerationModel(graph))

    assertThat(errors).isEmpty()
  }

  @Test
  fun `skips unavailable dependency handled by missing dependency validation`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("core")
      }
      moduleSet("core") {
        module("core.embedded", EMBEDDED)
      }
      plugin("not.bundled") {
        content("external.impl", loading = OPTIONAL)
      }
      linkContentModuleDeps("core.embedded", "external.impl")
    }

    val errors = runValidationRule(EmbeddedContentModuleDependencyValidator, testGenerationModel(graph))

    assertThat(errors).isEmpty()
  }
}
