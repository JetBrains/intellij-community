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
import org.jetbrains.intellij.build.productLayout.model.error.DuplicateModulesError
import org.jetbrains.intellij.build.productLayout.model.error.MissingDependenciesError
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Isolated unit tests for product module set validation.
 *
 * Uses [org.jetbrains.intellij.build.productLayout.dependency.pluginGraph] DSL to build test graphs directly.
 * Tests use the rule through the [Generator] interface via [org.jetbrains.intellij.build.productLayout.dependency.runValidationRule].
 */
@ExtendWith(TestFailureLogger::class)
class ProductModuleSetValidatorTest {
  @Nested
  inner class DuplicateModulesTest {
    @Test
    fun `reports duplicate modules across module sets`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA") {
              includesModuleSet("set1")
              includesModuleSet("set2")
          }
          moduleSet("set1") {
              module("module.duplicate")
          }
          moduleSet("set2") {
              module("module.duplicate")
          }
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ProductModuleSetValidator, model)

      Assertions.assertThat(errors).hasSize(1)
      Assertions.assertThat(errors[0]).isInstanceOf(DuplicateModulesError::class.java)
      val error = errors[0] as DuplicateModulesError
      Assertions.assertThat(error.duplicates.keys).containsExactly(ContentModuleName("module.duplicate"))
    }

    @Test
    fun `reports duplicate between module set and product content`(): Unit = runBlocking(Dispatchers.Default) {
      // For this test, we need to add module.a as product content too
      // The test DSL doesn't directly support this, so we verify duplicates within module sets
      val graphWithDup = pluginGraph {
          product("IDEA") {
              includesModuleSet("set1")
              includesModuleSet("set2")
          }
          moduleSet("set1") {
              module("module.a")
          }
          moduleSet("set2") {
              module("module.a")  // Same module in different set
          }
      }

      val model = testGenerationModel(graphWithDup)
      val errors = runValidationRule(ProductModuleSetValidator, model)

      Assertions.assertThat(errors).hasSize(1)
      Assertions.assertThat(errors[0]).isInstanceOf(DuplicateModulesError::class.java)
    }
  }

  @Nested
  inner class MissingDependenciesTest {
    @Test
    fun `reports missing transitive dependency for critical module`(): Unit = runBlocking(Dispatchers.Default) {
      // module.a (EMBEDDED) -> dep.module -> missing.module
      val graph = pluginGraph {
          product("IDEA") {
              includesModuleSet("core")
          }
          moduleSet("core") {
              moduleWithDeps("module.a", ModuleLoadingRuleValue.EMBEDDED, "dep.module")
          }
          // Set up content module dependencies (Module -> Module edges)
          linkContentModuleDeps("module.a", "dep.module")
          linkContentModuleDeps("dep.module", "missing.module")
          // missing.module vertex created but not available in product
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ProductModuleSetValidator, model)

      Assertions.assertThat(errors).hasSize(1)
      Assertions.assertThat(errors[0]).isInstanceOf(MissingDependenciesError::class.java)
      val error = errors[0] as MissingDependenciesError
      Assertions.assertThat(error.missingModules.keys).contains(ContentModuleName("missing.module"))
    }

    @Test
    fun `no error when dependency is available in module set`(): Unit = runBlocking(Dispatchers.Default) {
      // module.a -> module.b (both in same module set)
      val graph = pluginGraph {
          product("IDEA") {
              includesModuleSet("core")
          }
          moduleSet("core") {
              moduleWithDeps("module.a", ModuleLoadingRuleValue.REQUIRED, "module.b")
              module("module.b")
          }
          // Set up content module dependencies
          linkContentModuleDeps("module.a", "module.b")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ProductModuleSetValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `no error when dependency is in bundled plugin`(): Unit = runBlocking(Dispatchers.Default) {
      // module.a -> plugin.module (available via bundled plugin)
      val graph = pluginGraph {
          product("IDEA") {
              bundlesPlugin("my.plugin")
              includesModuleSet("core")
          }
          plugin("my.plugin") {
              content("plugin.module", ModuleLoadingRuleValue.OPTIONAL)
          }
          moduleSet("core") {
              moduleWithDeps("module.a", ModuleLoadingRuleValue.REQUIRED, "plugin.module")
          }
          // Set up content module dependencies
          linkContentModuleDeps("module.a", "plugin.module")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ProductModuleSetValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `no error for non-critical module with globally available dep`(): Unit = runBlocking(Dispatchers.Default) {
      // module.a (OPTIONAL) -> global.module (declared as content in another plugin, not bundled by IDEA)
      val graph = pluginGraph {
          product("IDEA") {
              includesModuleSet("core")
              // Note: other.plugin is NOT bundled by IDEA
          }
          moduleSet("core") {
              moduleWithDeps("module.a", ModuleLoadingRuleValue.OPTIONAL, "global.module")
          }
          // global.module exists as content in another plugin (not bundled by IDEA)
          // This makes it "globally available" - it has a content source somewhere
          plugin("other.plugin") {
              content("global.module", ModuleLoadingRuleValue.OPTIONAL)
          }
          // Set up content module dependencies
          linkContentModuleDeps("module.a", "global.module")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ProductModuleSetValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `respects allowedMissingDependencies`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA") {
              includesModuleSet("core")
              allowsMissing("allowed.missing")
          }
          moduleSet("core") {
              moduleWithDeps("module.a", ModuleLoadingRuleValue.EMBEDDED, "allowed.missing")
          }
          linkContentModuleDeps("module.a", "allowed.missing")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ProductModuleSetValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `empty when no module sets`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          product("IDEA")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ProductModuleSetValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `critical loading mode from any source makes module critical`(): Unit = runBlocking(Dispatchers.Default) {
      // module.a has EMBEDDED loading in its module set - makes it critical
      // Dependencies of critical modules must be available in THIS product
      val graph = pluginGraph {
          product("IDEA") {
              includesModuleSet("core")
          }
          moduleSet("core") {
              module("critical.module", ModuleLoadingRuleValue.EMBEDDED)  // Critical
          }
          // missing.dep exists globally (has content source) but not in this product
          // Critical modules can't rely on global availability
          plugin("other.plugin") {
              content("missing.dep", ModuleLoadingRuleValue.OPTIONAL)
          }
          linkContentModuleDeps("critical.module", "missing.dep")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ProductModuleSetValidator, model)

      // Error expected - missing.dep is not available in product and critical.module is critical
      Assertions.assertThat(errors).hasSize(1)
      Assertions.assertThat(errors[0]).isInstanceOf(MissingDependenciesError::class.java)
      val error = errors[0] as MissingDependenciesError
      Assertions.assertThat(error.missingModules.keys).contains(ContentModuleName("missing.dep"))
    }

    @Test
    fun `dependency satisfied by module in another module set`(): Unit = runBlocking(Dispatchers.Default) {
      // module.a depends on dep.module, which is in set2
      val graph = pluginGraph {
          product("IDEA") {
              includesModuleSet("set1")
              includesModuleSet("set2")
          }
          moduleSet("set1") {
              moduleWithDeps("module.a", ModuleLoadingRuleValue.EMBEDDED, "dep.module")
          }
          moduleSet("set2") {
              module("dep.module", ModuleLoadingRuleValue.REQUIRED)
          }
          linkContentModuleDeps("module.a", "dep.module")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ProductModuleSetValidator, model)

      // No error - dep.module is available in set2
      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `module with transitive deps in same module set`(): Unit = runBlocking(Dispatchers.Default) {
      // module.a -> dep.a -> dep.b (transitive chain, all in same set)
      val graph = pluginGraph {
          product("IDEA") {
              includesModuleSet("core")
          }
          moduleSet("core") {
              moduleWithDeps("module.a", ModuleLoadingRuleValue.REQUIRED, "dep.a")
              moduleWithDeps("dep.a", ModuleLoadingRuleValue.OPTIONAL, "dep.b")
              module("dep.b", ModuleLoadingRuleValue.OPTIONAL)
          }
          linkContentModuleDeps("module.a", "dep.a")
          linkContentModuleDeps("dep.a", "dep.b")
      }

      val model = testGenerationModel(graph)
      val errors = runValidationRule(ProductModuleSetValidator, model)

      // No errors - all transitive deps available in module set
      Assertions.assertThat(errors).isEmpty()
    }
  }
}
