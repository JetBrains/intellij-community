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
import org.jetbrains.intellij.build.productLayout.model.error.SelfContainedValidationError
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Isolated unit tests for [SelfContainedModuleSetValidator].
 *
 * Uses [org.jetbrains.intellij.build.productLayout.dependency.pluginGraph] DSL to build test graphs directly.
 * Tests use the rule through the [org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode] interface via
 * [org.jetbrains.intellij.build.productLayout.dependency.runValidationRule].
 */
@ExtendWith(TestFailureLogger::class)
class SelfContainedModuleSetValidatorTest {

  @Nested
  inner class SelfContainedModuleSetTest {
    @Test
    fun `no error when all deps are within the module set`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          moduleSet("core.platform", selfContained = true) {
              module("module.a", ModuleLoadingRuleValue.REQUIRED)
              module("module.b")
          }
          linkContentModuleDeps("module.a", "module.b")
      }

      val model = testGenerationModel(graph)

      val errors = runValidationRule(SelfContainedModuleSetValidator, model)

      Assertions.assertThat(errors).isEmpty()
    }

    @Test
    fun `reports missing dependency outside module set`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          moduleSet("core.platform", selfContained = true) {
              module("module.a", ModuleLoadingRuleValue.REQUIRED)
          }
          // external.module is not in the module set
          linkContentModuleDeps("module.a", "external.module")
      }

      val model = testGenerationModel(graph)

      val errors = runValidationRule(SelfContainedModuleSetValidator, model)

      Assertions.assertThat(errors).hasSize(1)
      val error = errors[0] as SelfContainedValidationError
      Assertions.assertThat(error.context).isEqualTo("core.platform")
      Assertions.assertThat(error.missingDependencies.keys).contains(ContentModuleName("external.module"))
    }

    @Test
    fun `reports transitive missing dependency`(): Unit = runBlocking(Dispatchers.Default) {
      // module.a -> module.b -> missing.module
      val graph = pluginGraph {
          moduleSet("core.platform", selfContained = true) {
              module("module.a", ModuleLoadingRuleValue.REQUIRED)
              module("module.b")
          }
          linkContentModuleDeps("module.a", "module.b")
          linkContentModuleDeps("module.b", "missing.module")
          // missing.module not in module set
      }

      val model = testGenerationModel(graph)

      val errors = runValidationRule(SelfContainedModuleSetValidator, model)

      Assertions.assertThat(errors).hasSize(1)
      val error = errors[0] as SelfContainedValidationError
      Assertions.assertThat(error.missingDependencies.keys).contains(ContentModuleName("missing.module"))
    }

    @Test
    fun `validates nested sets correctly`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
          moduleSet("parent.set", selfContained = true) {
              module("parent.module")
              nestedSet("child.set") {
                  module("child.module", ModuleLoadingRuleValue.REQUIRED)
              }
          }
          linkContentModuleDeps("child.module", "parent.module")
      }

      val model = testGenerationModel(graph)

      val errors = runValidationRule(SelfContainedModuleSetValidator, model)

      // child.module depends on parent.module which is in parent set - should be OK
      Assertions.assertThat(errors).isEmpty()
    }
  }

}
