// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("GrazieInspection", "GrazieStyle")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.PluginDependencyError
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Isolated unit tests for [PluginContentDependencyValidator].
 *
 * Tests plugin-level dependency validation (Tier 3 in validation hierarchy).
 * Uses [pluginGraph] DSL to build test graphs directly.
 *
 * Key behavior validated:
 * - Validation queries deps from graph edges (`EDGE_CONTENT_MODULE_DEPENDS_ON`), not raw JPS deps
 * - Test plugins include `EDGE_CONTENT_MODULE_DEPENDS_ON_TEST` edges (in addition to production edges)
 * - Missing dependencies are reported as [PluginDependencyError]
 */
@ExtendWith(TestFailureLogger::class)
class PluginContentDependencyValidatorTest {

  @Nested
  inner class GraphDependencyQueryTest {
    @Test
    fun `validation uses graph deps - available dep causes no error`(): Unit = runBlocking(Dispatchers.Default) {
      // This tests the fix: validation queries EDGE_CONTENT_MODULE_DEPENDS_ON from graph,
      // not raw JPS deps (which might include filtered/suppressed deps)
      val graph = pluginGraph {
        product("IDEA") {
          bundlesPlugin("my.plugin")
          includesModuleSet("core")
        }
        plugin("my.plugin") {
          content("content.module", ModuleLoadingRuleValue.REQUIRED)
        }
        moduleSet("core") {
          module("available.dep")
        }
        // Content module depends on available.dep via graph edge
        linkContentModuleDeps("content.module", "available.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      assertThat(errors).isEmpty()
    }

    @Test
    fun `validation ignores deps not in graph edges`(): Unit = runBlocking(Dispatchers.Default) {
      // If a dep is NOT in graph edges, validation should not check it.
      // This is the key regression test - previously validation used JPS deps which
      // included suppressed deps, causing false positives.
      val graph = pluginGraph {
        product("IDEA") {
          bundlesPlugin("my.plugin")
        }
        plugin("my.plugin") {
          content("content.module", ModuleLoadingRuleValue.REQUIRED)
        }
        // NO linkContentModuleDeps - graph has no deps for content.module
        // Even if JPS had deps, validation should not see them
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      // No errors because graph has no deps to validate
      assertThat(errors).isEmpty()
    }
  }

  @Nested
  inner class MissingDependencyTest {
    @Test
    fun `reports missing dependency not in any module set or plugin`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
        product("IDEA") {
          bundlesPlugin("my.plugin")
        }
        plugin("my.plugin") {
          content("content.module", ModuleLoadingRuleValue.REQUIRED)
        }
        // Dep exists in graph but is not available anywhere
        linkContentModuleDeps("content.module", "missing.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      assertThat(errors).hasSize(1)
      assertThat(errors[0]).isInstanceOf(PluginDependencyError::class.java)
      val error = errors[0] as PluginDependencyError
      assertThat(error.missingDependencies.keys).contains(ContentModuleName("missing.dep"))
    }

    @Test
    fun `no error when dependency is in another bundled plugin`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
        product("IDEA") {
          bundlesPlugin("my.plugin")
          bundlesPlugin("other.plugin")
        }
        plugin("my.plugin") {
          content("content.module", ModuleLoadingRuleValue.REQUIRED)
        }
        plugin("other.plugin") {
          content("dep.module", ModuleLoadingRuleValue.OPTIONAL)
        }
        linkContentModuleDeps("content.module", "dep.module")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      assertThat(errors).isEmpty()
    }

    @Test
    fun `missing dependency in one bundled product reports error`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
        product("IDEA") {
          bundlesPlugin("my.plugin")
          includesModuleSet("core")
        }
        product("PY") {
          bundlesPlugin("my.plugin")
        }
        plugin("my.plugin") {
          content("content.module", ModuleLoadingRuleValue.REQUIRED)
        }
        moduleSet("core") {
          module("dep.module")
        }
        linkContentModuleDeps("content.module", "dep.module")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      assertThat(errors).hasSize(1)
      val error = errors[0] as PluginDependencyError
      assertThat(error.unresolvedByProduct.keys).contains("PY")
      assertThat(error.missingDependencies.keys).contains(ContentModuleName("dep.module"))
    }

    @Test
    fun `no error when dependency is in same plugin`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
        product("IDEA") {
          bundlesPlugin("my.plugin")
        }
        plugin("my.plugin") {
          content("content.module.a", ModuleLoadingRuleValue.REQUIRED)
          content("content.module.b", ModuleLoadingRuleValue.OPTIONAL)
        }
        linkContentModuleDeps("content.module.a", "content.module.b")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      assertThat(errors).isEmpty()
    }
  }

  @Nested
  inner class FilteredDependencyAllowlistTest {
    @Test
    fun `allowed test library module is ignored in filtered deps`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
        product("IDEA") {
          bundlesPlugin("my.plugin")
        }
        plugin("my.plugin") {
          content("content.module", ModuleLoadingRuleValue.REQUIRED)
        }

        // JPS dependency exists but no XML dep edge -> implicit (filtered) dependency.
        moduleWithScopedDeps("content.module", "intellij.libraries.assertj.core" to "COMPILE")
        moduleWithDeps("intellij.libraries.assertj.core")
      }

      val model = testGenerationModel(
        graph,
        testLibraryAllowedInModule = mapOf(
          ContentModuleName("content.module") to setOf("intellij.libraries.assertj.core"),
        ),
      )
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      assertThat(errors).isEmpty()
    }
  }

  @Nested
  inner class TestPluginEdgeTypeTest {
    @Test
    fun `test plugin uses TEST edge type - sees test deps`(): Unit = runBlocking(Dispatchers.Default) {
      // Test plugins should include EDGE_CONTENT_MODULE_DEPENDS_ON_TEST
      val graph = pluginGraph {
        product("IDEA") {
          bundlesTestPlugin("test.plugin")
          includesModuleSet("core")
        }
        testPlugin("test.plugin") {
          content("test.content", ModuleLoadingRuleValue.REQUIRED)
        }
        moduleSet("core") {
          module("test.dep")
        }
        // Test dep added via TEST edge type
        linkContentModuleTestDeps("test.content", "test.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      // No error - test.dep is available and test plugin sees TEST edges
      assertThat(errors).isEmpty()
    }

    @Test
    fun `test plugin reports missing production dep`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
        product("IDEA") {
          bundlesTestPlugin("test.plugin")
        }
        testPlugin("test.plugin") {
          content("test.content", ModuleLoadingRuleValue.REQUIRED)
        }
        // Production edge only, no content source for missing.prod.dep
        linkContentModuleDeps("test.content", "missing.prod.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      assertThat(errors).hasSize(1)
      val error = errors[0] as PluginDependencyError
      assertThat(error.missingDependencies.keys).contains(ContentModuleName("missing.prod.dep"))
    }

    @Test
    fun `production plugin ignores TEST-only deps`(): Unit = runBlocking(Dispatchers.Default) {
      // Production plugins should NOT see deps that are only in TEST edges
      val graph = pluginGraph {
        product("IDEA") {
          bundlesPlugin("prod.plugin")
        }
        plugin("prod.plugin") {
          content("prod.content", ModuleLoadingRuleValue.REQUIRED)
        }
        // Only TEST edge, no production edge
        linkContentModuleTestDeps("prod.content", "test.only.dep")
        // Note: no linkContentModuleDeps for prod.content
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      // No error - production validation uses EDGE_CONTENT_MODULE_DEPENDS_ON,
      // which has no deps for prod.content
      assertThat(errors).isEmpty()
    }

    @Test
    fun `test plugin with missing test dep reports error`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
        product("IDEA") {
          bundlesTestPlugin("test.plugin")
        }
        testPlugin("test.plugin") {
          content("test.content", ModuleLoadingRuleValue.REQUIRED)
        }
        // Test dep not available anywhere
        linkContentModuleTestDeps("test.content", "missing.test.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      assertThat(errors).hasSize(1)
      assertThat(errors[0]).isInstanceOf(PluginDependencyError::class.java)
      val error = errors[0] as PluginDependencyError
      assertThat(error.missingDependencies.keys).contains(ContentModuleName("missing.test.dep"))
    }
  }


  @Nested
  inner class EmptyAndEdgeCasesTest {
    @Test
    fun `no error when no bundled plugins`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
        product("IDEA")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      assertThat(errors).isEmpty()
    }

    @Test
    fun `no error when plugin has no content modules`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
        product("IDEA") {
          bundlesPlugin("empty.plugin")
        }
        plugin("empty.plugin") {
          // no content
        }
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(PluginContentDependencyValidator, model)

      assertThat(errors).isEmpty()
    }
  }
}
