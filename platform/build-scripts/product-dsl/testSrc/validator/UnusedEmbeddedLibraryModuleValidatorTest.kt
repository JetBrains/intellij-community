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
import org.jetbrains.intellij.build.productLayout.moduleSet
import org.jetbrains.intellij.build.productLayout.model.error.UnusedEmbeddedLibraryModuleError
import org.jetbrains.intellij.build.productLayout.productModules
import org.jetbrains.intellij.build.productLayout.traversal.analyzeUnusedEmbeddedLibraryModules
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class UnusedEmbeddedLibraryModuleValidatorTest {
  @Test
  fun `keeps library chain reachable from embedded platform content`() {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") {
        module("intellij.platform.core", EMBEDDED)
        module("intellij.libraries.first", EMBEDDED)
        module("intellij.libraries.second", EMBEDDED)
      }
      linkContentModuleDeps("intellij.platform.core", "intellij.libraries.first")
      linkContentModuleDeps("intellij.libraries.first", "intellij.libraries.second")
    }

    assertThat(analyzeUnusedEmbeddedLibraryModules(graph).violations).isEmpty()
  }

  @Test
  fun `reports plugin-only library and its transitive plugin consumers`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("core")
        bundlesPlugin("first.plugin")
        bundlesPlugin("second.plugin")
      }
      moduleSet("core") {
        module("intellij.libraries.shared", EMBEDDED)
      }
      plugin("first.plugin") { content("first.plugin.impl") }
      plugin("second.plugin") { content("second.plugin.impl") }
      linkContentModuleDeps("first.plugin.impl", "intellij.libraries.shared")
      linkContentModuleDeps("second.plugin.impl", "first.plugin.impl")
    }

    val errors = runValidationRule(UnusedEmbeddedLibraryModuleValidator, testGenerationModel(graph))

    assertThat(errors).hasSize(1)
    val violation = (errors.single() as UnusedEmbeddedLibraryModuleError).violations.single()
    assertThat(violation.module).isEqualTo("intellij.libraries.shared")
    assertThat(violation.productionPluginConsumers).containsExactly("first.plugin", "second.plugin")
  }

  @Test
  fun `reports plugin-level module dependency as plugin consumer`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("core")
        bundlesPlugin("consumer.plugin")
      }
      moduleSet("core") { module("intellij.libraries.shared", EMBEDDED) }
      plugin("consumer.plugin") {
        dependsOnContentModule("intellij.libraries.shared")
      }
    }

    val errors = runValidationRule(UnusedEmbeddedLibraryModuleValidator, testGenerationModel(graph))

    val violation = (errors.single() as UnusedEmbeddedLibraryModuleError).violations.single()
    assertThat(violation.productionPluginConsumers).containsExactly("consumer.plugin")
  }

  @Test
  fun `keeps library reached through embedded JPS runtime closure`() {
    val core = moduleSet("core", includeDependencies = true) {
      embeddedModule("intellij.platform.core")
      embeddedModule("intellij.libraries.implicit")
    }
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") {
        module("intellij.platform.core", EMBEDDED)
        module("intellij.libraries.implicit", EMBEDDED)
      }
      moduleWithDeps("intellij.platform.core", "intellij.libraries.implicit")
      moduleWithDeps("intellij.libraries.implicit")
    }

    val spec = productModules { moduleSet(core) }
    val result = analyzeUnusedEmbeddedLibraryModules(
      graph = graph,
      productSpecsByName = mapOf("IDEA" to spec),
    )

    assertThat(result.violations).isEmpty()
  }

  @Test
  fun `keeps library reached from embedded product target without dependency expansion`() {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") {
        module("intellij.platform.legacy", EMBEDDED)
        module("intellij.libraries.core.only", EMBEDDED)
      }
      moduleWithDeps("intellij.platform.legacy", "intellij.libraries.core.only")
      moduleWithDeps("intellij.libraries.core.only")
    }

    assertThat(analyzeUnusedEmbeddedLibraryModules(graph).violations).isEmpty()
  }

  @Test
  fun `plugin main target does not justify embedding`() {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("core")
        bundlesPlugin("consumer.plugin")
      }
      moduleSet("core") { module("intellij.libraries.plugin.only", EMBEDDED) }
      plugin("consumer.plugin") { pluginId("consumer.plugin") }
      target("consumer.plugin") { dependsOn("intellij.libraries.plugin.only") }
      moduleWithDeps("intellij.libraries.plugin.only")
      linkPluginMainTarget("consumer.plugin")
    }

    assertThat(analyzeUnusedEmbeddedLibraryModules(graph).violations.map { it.module })
      .containsExactly("intellij.libraries.plugin.only")
  }

  @Test
  fun `optional and required platform consumers do not justify embedding`() {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") {
        module("intellij.platform.optional", OPTIONAL)
        module("intellij.platform.required", REQUIRED)
        module("intellij.libraries.optional.dep", EMBEDDED)
        module("intellij.libraries.required.dep", EMBEDDED)
      }
      linkContentModuleDeps("intellij.platform.optional", "intellij.libraries.optional.dep")
      linkContentModuleDeps("intellij.platform.required", "intellij.libraries.required.dep")
    }

    val result = analyzeUnusedEmbeddedLibraryModules(graph)

    assertThat(result.violations.map { it.module }).containsExactlyInAnyOrder(
      "intellij.libraries.optional.dep",
      "intellij.libraries.required.dep",
    )
  }

  @Test
  fun `test dependencies are diagnostics only`() {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("core")
        bundlesTestPlugin("test.plugin")
      }
      moduleSet("core") { module("intellij.libraries.test.only", EMBEDDED) }
      testPlugin("test.plugin") { testContent("test.plugin.impl") }
      linkContentModuleTestDeps("test.plugin.impl", "intellij.libraries.test.only")
    }

    val violation = analyzeUnusedEmbeddedLibraryModules(graph).violations.single()

    assertThat(violation.productionPluginConsumers).isEmpty()
    assertThat(violation.testPluginConsumers).containsExactly("test.plugin")
  }

  @Test
  fun `finds embedded libraries in nested module sets`() {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") {
        nestedSet("libraries.nested") {
          module("intellij.libraries.nested", EMBEDDED)
        }
      }
    }

    val violation = analyzeUnusedEmbeddedLibraryModules(graph).violations.single()

    assertThat(violation.declaringModuleSets).containsExactly("libraries.nested")
    assertThat(violation.availableProducts).containsExactly("IDEA")
  }

  @Test
  fun `dead embedded library cycle does not make itself live`() {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") {
        module("intellij.libraries.first", EMBEDDED)
        module("intellij.libraries.second", EMBEDDED)
      }
      linkContentModuleDeps("intellij.libraries.first", "intellij.libraries.second")
      linkContentModuleDeps("intellij.libraries.second", "intellij.libraries.first")
    }

    assertThat(analyzeUnusedEmbeddedLibraryModules(graph).violations.map { it.module }).containsExactlyInAnyOrder(
      "intellij.libraries.first",
      "intellij.libraries.second",
    )
  }

  @Test
  fun `embedded use in one product keeps globally shared library`() {
    val graph = pluginGraph {
      product("IDEA") {
        includesModuleSet("libraries")
        content("intellij.platform.idea", EMBEDDED)
      }
      product("Rider") { includesModuleSet("libraries") }
      moduleSet("libraries") { module("intellij.libraries.shared", EMBEDDED) }
      linkContentModuleDeps("intellij.platform.idea", "intellij.libraries.shared")
    }

    assertThat(analyzeUnusedEmbeddedLibraryModules(graph).violations).isEmpty()
  }

  @Test
  fun `dependency from a product where library is unavailable does not count`() {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("libraries") }
      product("Rider") { content("intellij.platform.rider", EMBEDDED) }
      moduleSet("libraries") { module("intellij.libraries.idea.only", EMBEDDED) }
      linkContentModuleDeps("intellij.platform.rider", "intellij.libraries.idea.only")
    }

    assertThat(analyzeUnusedEmbeddedLibraryModules(graph).violations.map { it.module })
      .containsExactly("intellij.libraries.idea.only")
  }

  @Test
  fun `skips libraries allowlisted for the core classloader`() {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") {
        module("intellij.libraries.cglib", EMBEDDED)
        module("intellij.libraries.plugin.only", EMBEDDED)
      }
    }

    assertThat(analyzeUnusedEmbeddedLibraryModules(graph).violations.map { it.module })
      .containsExactly("intellij.libraries.plugin.only")
  }

  @Test
  fun `ignores non-library and ordinary library modules`() {
    val graph = pluginGraph {
      product("IDEA") { includesModuleSet("core") }
      moduleSet("core") {
        module("intellij.platform.unused", EMBEDDED)
        module("intellij.libraries.optional", OPTIONAL)
      }
    }

    assertThat(analyzeUnusedEmbeddedLibraryModules(graph).violations).isEmpty()
  }
}
