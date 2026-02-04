// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.MissingDependenciesError
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Isolated unit tests for plugin content module validation.
 *
 * Uses [org.jetbrains.intellij.build.productLayout.dependency.pluginGraph] DSL to build test graphs directly.
 */
@ExtendWith(TestFailureLogger::class)
class ContentModuleDependencyValidatorTest {
  @Nested
  inner class MissingDependenciesTest {
    @Test
    fun `reports missing transitive dependency for critical plugin module`(): Unit = runBlocking(Dispatchers.Default) {
      // plugin.module (EMBEDDED) -> dep.module -> missing.module
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("my.plugin")
          }
          plugin("my.plugin") {
              content("plugin.module", ModuleLoadingRuleValue.EMBEDDED)
          }
          // Set up content module dependencies (Module -> Module edges)
          linkContentModuleDeps("plugin.module", "dep.module")
          linkContentModuleDeps("dep.module", "missing.module")
          // missing.module vertex created but not available in product
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      Assertions.assertThat(errors).hasSize(1)
      Assertions.assertThat(errors[0]).isInstanceOf(MissingDependenciesError::class.java)
      val error = errors[0] as MissingDependenciesError
      Assertions.assertThat(error.missingModules.keys).contains(ContentModuleName("missing.module"))
    }

    @Test
    fun `no error when dependency is available in module set`(): Unit = runBlocking(Dispatchers.Default) {
      // plugin.module -> available.module (in module set)
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("my.plugin")
              includesModuleSet("core")
          }
          plugin("my.plugin") {
              content("plugin.module", ModuleLoadingRuleValue.REQUIRED)
          }
          moduleSet("core") {
              module("available.module")
          }
          // Set up content module dependencies
          linkContentModuleDeps("plugin.module", "available.module")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `no error when dependency is in same plugin`(): Unit = runBlocking(Dispatchers.Default) {
      // plugin.module.a -> plugin.module.b (both in same plugin)
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("my.plugin")
          }
          plugin("my.plugin") {
              content("plugin.module.a", ModuleLoadingRuleValue.REQUIRED)
              content("plugin.module.b", ModuleLoadingRuleValue.REQUIRED)
          }
          // Set up content module dependencies
          linkContentModuleDeps("plugin.module.a", "plugin.module.b")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `no error for non-critical module with globally available dep`(): Unit = runBlocking(Dispatchers.Default) {
      // plugin.module (OPTIONAL) -> global.module (declared as content in another plugin, not bundled by IDEA)
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("my.plugin")
              // Note: other.plugin is NOT bundled by IDEA
          }
          plugin("my.plugin") {
              content("plugin.module", ModuleLoadingRuleValue.OPTIONAL)
          }
          // global.module exists as content in another plugin (not bundled by IDEA)
          // This makes it "globally available" - it has a content source somewhere
          plugin("other.plugin") {
              content("global.module", ModuleLoadingRuleValue.OPTIONAL)
          }
          // Set up content module dependencies - global.module exists but not in IDEA
          linkContentModuleDeps("plugin.module", "global.module")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `test dependencies do not leak into production validation`(): Unit = runBlocking(Dispatchers.Default) {
      // Scenario: intellij.platform.lang has a TEST scope dep on hamcrest in IML
      // Production validation should NOT report hamcrest as missing
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("my.plugin")
          }
          plugin("my.plugin") {
              content("platform.lang", ModuleLoadingRuleValue.EMBEDDED)
          }
          // Production deps - available.module is satisfied
          linkContentModuleDeps("platform.lang", "available.module")
          // Test deps - hamcrest is ONLY in test edges, NOT in prod edges
          linkContentModuleTestDeps("platform.lang", "available.module", "intellij.libraries.hamcrest")
          // hamcrest vertex exists but is not available in product - this should NOT cause an error
          // because production validation uses EDGE_CONTENT_MODULE_DEPENDS_ON, not EDGE_CONTENT_MODULE_DEPENDS_ON_TEST

          // Make available.module actually available in the product
          plugin("my.plugin") {
              content("available.module", ModuleLoadingRuleValue.OPTIONAL)
          }
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      // No error - hamcrest is only in test edges, production validation ignores it
      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `respects allowedMissingDependencies`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("my.plugin")
              allowsMissing("allowed.missing")
          }
          plugin("my.plugin") {
              content("plugin.module", ModuleLoadingRuleValue.EMBEDDED)
          }
          linkContentModuleDeps("plugin.module", "allowed.missing")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `empty when no bundled plugins`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `handles content module available in multiple plugins`(): Unit = runBlocking(Dispatchers.Default) {
      // shared.module is content in both plugin.a and plugin.b with different loading modes.
      // It should be validated once (deduplicated), and treated as critical if ANY plugin has it EMBEDDED/REQUIRED.
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("plugin.a")
              bundlesPlugin("plugin.b")
          }
          plugin("plugin.a") {
              content("shared.module", ModuleLoadingRuleValue.REQUIRED)  // Critical
              content("available.dep", ModuleLoadingRuleValue.OPTIONAL)
          }
          plugin("plugin.b") {
              content(
                  "shared.module",
                  ModuleLoadingRuleValue.OPTIONAL
              )  // Not critical, but shared.module is still critical due to plugin.a
          }
          linkContentModuleDeps("shared.module", "available.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      // No error - available.dep is in plugin.a's content
      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `critical loading mode from any plugin makes module critical`(): Unit = runBlocking(Dispatchers.Default) {
      // shared.module is OPTIONAL in plugin.a but EMBEDDED in plugin.b
      // The module should be treated as critical (EMBEDDED wins)
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("plugin.a")
              bundlesPlugin("plugin.b")
          }
          plugin("plugin.a") {
              content("shared.module", ModuleLoadingRuleValue.OPTIONAL)
          }
          plugin("plugin.b") {
              content("shared.module", ModuleLoadingRuleValue.EMBEDDED)  // Makes it critical
          }
          // missing.dep exists globally but not in this product - critical modules can't rely on global availability
          linkContentModuleDeps("shared.module", "missing.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      // Error expected - missing.dep is not available in product and shared.module is critical
      Assertions.assertThat(errors).hasSize(1)
      Assertions.assertThat(errors[0]).isInstanceOf(MissingDependenciesError::class.java)
      val error = errors[0] as MissingDependenciesError
      Assertions.assertThat(error.missingModules.keys).contains(ContentModuleName("missing.dep"))
    }
  }

  @Nested
  inner class MultiPluginLoadingModeCombinationsTest {
    @Test
    fun `OPTIONAL in all plugins - missing global dep is OK`(): Unit = runBlocking(Dispatchers.Default) {
      // Module is OPTIONAL in both plugins - global dep (declared as content elsewhere) is acceptable
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("plugin.a")
              bundlesPlugin("plugin.b")
              // Note: plugin.other is NOT bundled by IDEA
          }
          plugin("plugin.a") {
              content("shared.module", ModuleLoadingRuleValue.OPTIONAL)
          }
          plugin("plugin.b") {
              content("shared.module", ModuleLoadingRuleValue.OPTIONAL)
          }
          // global.dep is declared as content in another plugin (not bundled by IDEA)
          // This makes it "globally available" - it has a content source somewhere
          plugin("plugin.other") {
              content("global.dep", ModuleLoadingRuleValue.OPTIONAL)
          }
          linkContentModuleDeps("shared.module", "global.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)
      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `ON_DEMAND in all plugins - missing global dep is OK`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("plugin.a")
              bundlesPlugin("plugin.b")
              // Note: plugin.other is NOT bundled by IDEA
          }
          plugin("plugin.a") {
              content("shared.module", ModuleLoadingRuleValue.ON_DEMAND)
          }
          plugin("plugin.b") {
              content("shared.module", ModuleLoadingRuleValue.ON_DEMAND)
          }
          // global.dep is declared as content in another plugin (not bundled by IDEA)
          // This makes it "globally available" - it has a content source somewhere
          plugin("plugin.other") {
              content("global.dep", ModuleLoadingRuleValue.OPTIONAL)
          }
          linkContentModuleDeps("shared.module", "global.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)
      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `REQUIRED in one plugin makes module critical`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("plugin.a")
              bundlesPlugin("plugin.b")
          }
          plugin("plugin.a") {
              content("shared.module", ModuleLoadingRuleValue.ON_DEMAND)
          }
          plugin("plugin.b") {
              content("shared.module", ModuleLoadingRuleValue.REQUIRED)  // Makes it critical
          }
          linkContentModuleDeps("shared.module", "missing.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      Assertions.assertThat(errors).hasSize(1)
      val error = errors[0] as MissingDependenciesError
      Assertions.assertThat(error.missingModules.keys).contains(ContentModuleName("missing.dep"))
    }

    @Test
    fun `EMBEDDED in one plugin makes module critical`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("plugin.a")
              bundlesPlugin("plugin.b")
              bundlesPlugin("plugin.c")
          }
          plugin("plugin.a") {
              content("shared.module", ModuleLoadingRuleValue.OPTIONAL)
          }
          plugin("plugin.b") {
              content("shared.module", ModuleLoadingRuleValue.ON_DEMAND)
          }
          plugin("plugin.c") {
              content("shared.module", ModuleLoadingRuleValue.EMBEDDED)  // Makes it critical
          }
          linkContentModuleDeps("shared.module", "missing.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      Assertions.assertThat(errors).hasSize(1)
      val error = errors[0] as MissingDependenciesError
      Assertions.assertThat(error.missingModules.keys).contains(ContentModuleName("missing.dep"))
    }

    @Test
    fun `dependency satisfied by another plugin content`(): Unit = runBlocking(Dispatchers.Default) {
      // shared.module depends on dep.module, which is content of plugin.b
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("plugin.a")
              bundlesPlugin("plugin.b")
          }
          plugin("plugin.a") {
              content("shared.module", ModuleLoadingRuleValue.EMBEDDED)
          }
          plugin("plugin.b") {
              content("shared.module", ModuleLoadingRuleValue.OPTIONAL)
              content("dep.module", ModuleLoadingRuleValue.REQUIRED)
          }
          linkContentModuleDeps("shared.module", "dep.module")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      // No error - dep.module is available in plugin.b
      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `mixed loading modes across three plugins`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("plugin.a")
              bundlesPlugin("plugin.b")
              bundlesPlugin("plugin.c")
              includesModuleSet("core")
          }
          plugin("plugin.a") {
              content("module.x", ModuleLoadingRuleValue.OPTIONAL)
              content("module.y", ModuleLoadingRuleValue.EMBEDDED)
          }
          plugin("plugin.b") {
              content("module.x", ModuleLoadingRuleValue.REQUIRED)  // Makes module.x critical
              content("module.z", ModuleLoadingRuleValue.ON_DEMAND)
          }
          plugin("plugin.c") {
              content("module.y", ModuleLoadingRuleValue.ON_DEMAND)
              content("module.z", ModuleLoadingRuleValue.OPTIONAL)
          }
          moduleSet("core") {
              module("core.dep")
          }
          // All modules depend on core.dep which is in module set
          linkContentModuleDeps("module.x", "core.dep")
          linkContentModuleDeps("module.y", "core.dep")
          linkContentModuleDeps("module.z", "core.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      // No errors - all deps satisfied via module set
      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `module in multiple plugins with transitive deps`(): Unit = runBlocking(Dispatchers.Default) {
      // shared.module -> dep.a -> dep.b (transitive chain)
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("plugin.a")
              bundlesPlugin("plugin.b")
          }
          plugin("plugin.a") {
              content("shared.module", ModuleLoadingRuleValue.REQUIRED)
              content("dep.a", ModuleLoadingRuleValue.OPTIONAL)
              content("dep.b", ModuleLoadingRuleValue.OPTIONAL)
          }
          plugin("plugin.b") {
              content("shared.module", ModuleLoadingRuleValue.OPTIONAL)
          }
          linkContentModuleDeps("shared.module", "dep.a")
          linkContentModuleDeps("dep.a", "dep.b")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      // No errors - all transitive deps available in plugin.a
      Assertions.assertThat(errors).isEmpty()
    }
  }

  /**
   * Tests for slash-notation modules (e.g., "intellij.restClient/intelliLang").
   *
   * Slash-notation modules are virtual content modules:
   * - No separate JPS module - descriptor is in parent plugin's resource root
   * - Descriptor file naming: parent.subModule.xml (dots, not slashes)
   * - Example: intellij.restClient/intelliLang → intellij.restClient.intelliLang.xml
   */
  @Nested
  inner class SlashNotationModuleTest {
    @Test
    fun `slash-notation module as content is valid`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("intellij.restClient")
          }
          plugin("intellij.restClient") {
              content("intellij.restClient/intelliLang", ModuleLoadingRuleValue.OPTIONAL)
          }
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `dependency on slash-notation module resolves correctly`(): Unit = runBlocking(Dispatchers.Default) {
      // regular.module depends on intellij.restClient/intelliLang
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("my.plugin")
              bundlesPlugin("intellij.restClient")
          }
          plugin("my.plugin") {
              content("regular.module", ModuleLoadingRuleValue.REQUIRED)
          }
          plugin("intellij.restClient") {
              content("intellij.restClient/intelliLang", ModuleLoadingRuleValue.OPTIONAL)
          }
          linkContentModuleDeps("regular.module", "intellij.restClient/intelliLang")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      // No error - slash-notation module is available as content in intellij.restClient
      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `slash-notation module with dependencies validates correctly`(): Unit = runBlocking(Dispatchers.Default) {
      // intellij.restClient/intelliLang depends on some.dep
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("intellij.restClient")
              includesModuleSet("core")
          }
          plugin("intellij.restClient") {
              content("intellij.restClient/intelliLang", ModuleLoadingRuleValue.REQUIRED)
          }
          moduleSet("core") {
              module("some.dep")
          }
          linkContentModuleDeps("intellij.restClient/intelliLang", "some.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      // No error - some.dep is available in module set
      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `missing dependency for slash-notation module is reported`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("intellij.restClient")
          }
          plugin("intellij.restClient") {
              content("intellij.restClient/intelliLang", ModuleLoadingRuleValue.EMBEDDED)
          }
          linkContentModuleDeps("intellij.restClient/intelliLang", "missing.module")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      Assertions.assertThat(errors).hasSize(1)
      Assertions.assertThat(errors[0]).isInstanceOf(MissingDependenciesError::class.java)
      val error = errors[0] as MissingDependenciesError
      Assertions.assertThat(error.missingModules.keys).contains(ContentModuleName("missing.module"))
    }

    @Test
    fun `multiple slash-notation modules in same plugin`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("intellij.restClient")
          }
          plugin("intellij.restClient") {
              content("intellij.restClient/intelliLang", ModuleLoadingRuleValue.OPTIONAL)
              content("intellij.restClient/jsonPath", ModuleLoadingRuleValue.OPTIONAL)
          }
          // One depends on the other
          linkContentModuleDeps("intellij.restClient/intelliLang", "intellij.restClient/jsonPath")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      // No error - both are content in same plugin
      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `slash-notation module not bundled causes missing dependency error`(): Unit = runBlocking(Dispatchers.Default) {
      // regular.module depends on intellij.restClient/intelliLang, but intellij.restClient is not bundled
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("my.plugin")
              // Note: intellij.restClient is NOT bundled
          }
          plugin("my.plugin") {
              content("regular.module", ModuleLoadingRuleValue.REQUIRED)
          }
          plugin("intellij.restClient") {
              content("intellij.restClient/intelliLang", ModuleLoadingRuleValue.OPTIONAL)
          }
          linkContentModuleDeps("regular.module", "intellij.restClient/intelliLang")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      // Error - slash-notation module is globally available (has content source) but not in product
      // For REQUIRED loading mode, this should report missing dependency
      Assertions.assertThat(errors).hasSize(1)
      Assertions.assertThat(errors[0]).isInstanceOf(MissingDependenciesError::class.java)
    }

    @Test
    fun `OPTIONAL module can depend on globally available slash-notation module`(): Unit = runBlocking(Dispatchers.Default) {
      // OPTIONAL regular.module depends on intellij.restClient/intelliLang which is not bundled but exists globally
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("my.plugin")
              // Note: intellij.restClient is NOT bundled by IDEA
          }
          plugin("my.plugin") {
              content("regular.module", ModuleLoadingRuleValue.OPTIONAL)  // Non-critical
          }
          // Slash-notation module exists globally (has content source in another plugin)
          plugin("intellij.restClient") {
              content("intellij.restClient/intelliLang", ModuleLoadingRuleValue.OPTIONAL)
          }
          linkContentModuleDeps("regular.module", "intellij.restClient/intelliLang")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ContentModuleDependencyValidator, model)

      // No error - OPTIONAL modules can depend on globally available modules
      Assertions.assertThat(errors).isEmpty()
    }
  }
}
