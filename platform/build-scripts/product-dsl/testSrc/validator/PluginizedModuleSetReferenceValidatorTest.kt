// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.productLayout.model.error.PluginizedModuleSetReferenceError
import org.jetbrains.intellij.build.productLayout.moduleSet
import org.jetbrains.intellij.build.productLayout.plugin
import org.jetbrains.intellij.build.productLayout.productModules
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class PluginizedModuleSetReferenceValidatorTest {
  @Test
  fun `reports product that references pluginized module set directly`(): Unit = runBlocking(Dispatchers.Default) {
    val pluginized = plugin("debugger.streams") {
      module("intellij.debugger.streams.core")
    }

    val model = modelWithProductsAndModuleSets(
      moduleSets = listOf(pluginized),
      products = listOf(
        ideaProduct {
          moduleSet(pluginized)
        }
      ),
    )
    val errors = runValidationRule(PluginizedModuleSetReferenceValidator, model)

    val error = errors.filterIsInstance<PluginizedModuleSetReferenceError>().single()
    assertThat(error.context).isEqualTo("IDEA")
    assertThat(error.pluginizedModuleSetName).isEqualTo("debugger.streams")
    assertThat(error.ownerKind).isEqualTo(PluginizedModuleSetReferenceError.OwnerKind.PRODUCT)
  }

  @Test
  fun `reports product that references pluginized module set with overrides`(): Unit = runBlocking(Dispatchers.Default) {
    val pluginized = plugin("debugger.streams") {
      module("intellij.debugger.streams.core")
    }

    val model = modelWithProductsAndModuleSets(
      moduleSets = listOf(pluginized),
      products = listOf(
        ideaProduct {
          moduleSet(pluginized) {
            overrideAsEmbedded("intellij.debugger.streams.core")
          }
        }
      ),
    )
    val errors = runValidationRule(PluginizedModuleSetReferenceValidator, model)

    val error = errors.filterIsInstance<PluginizedModuleSetReferenceError>().single()
    assertThat(error.context).isEqualTo("IDEA")
    assertThat(error.pluginizedModuleSetName).isEqualTo("debugger.streams")
    assertThat(error.ownerKind).isEqualTo(PluginizedModuleSetReferenceError.OwnerKind.PRODUCT)
  }

  @Test
  fun `reports regular module set that nests pluginized module set transitively`(): Unit = runBlocking(Dispatchers.Default) {
    val pluginized = plugin("debugger.streams") {
      module("intellij.debugger.streams.core")
    }
    val intermediate = moduleSet("intermediate") {
      module("intellij.intermediate")
      moduleSet(pluginized)
    }
    val regularParent = moduleSet("regular.parent") {
      module("intellij.regular.parent")
      moduleSet(intermediate)
    }

    val model = modelWithProductsAndModuleSets(moduleSets = listOf(regularParent, pluginized))
    val errors = runValidationRule(PluginizedModuleSetReferenceValidator, model)

    val error = errors.filterIsInstance<PluginizedModuleSetReferenceError>().single()
    assertThat(error.context).isEqualTo("regular.parent")
    assertThat(error.pluginizedModuleSetName).isEqualTo("debugger.streams")
    assertThat(error.ownerKind).isEqualTo(PluginizedModuleSetReferenceError.OwnerKind.MODULE_SET)
  }

  private fun modelWithProductsAndModuleSets(
    moduleSets: List<ModuleSet>,
    products: List<DiscoveredProduct> = emptyList(),
  ) =
    testGenerationModel(pluginGraph {})
      .let { model ->
        model.copy(
          discovery = model.discovery.copy(
            moduleSetsByLabel = mapOf("community" to moduleSets),
            products = products,
          )
        )
      }

  private fun ideaProduct(
    specBuilder: org.jetbrains.intellij.build.productLayout.ProductModulesContentSpecBuilder.() -> Unit,
  ) =
    DiscoveredProduct(
      name = "IDEA",
      config = ProductConfiguration(modules = emptyList(), className = "IdeaProperties", pluginXmlPath = null),
      properties = null,
      spec = productModules(specBuilder),
      pluginXmlPath = null,
    )
}
