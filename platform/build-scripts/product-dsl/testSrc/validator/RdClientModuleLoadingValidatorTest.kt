// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.buildScripts.concurrency.SharedTaskOwner
import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.TestPluginGraphBuilder
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.productLayout.model.error.RdClientModuleLoadingError
import org.jetbrains.intellij.build.productLayout.model.error.errorId
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class RdClientModuleLoadingValidatorTest {
  @Test
  fun `bundled on demand modules stay inactive without consumers`() {
    val graph = pluginGraph {
      rdProduct("idea")
      product("idea") { content("available") }
      linkContentModuleDeps(CLIENT, BASE, "available")
    }
    assertThat(validate(graph, "idea")).isEmpty()
  }

  @Test
  fun `transitive demand reports the activation chain`() {
    val graph = pluginGraph {
      rdProduct("idea")
      product("idea") { content("consumer") }
      linkContentModuleDeps("consumer", CLIENT)
      linkContentModuleDeps(CLIENT, BASE)
    }
    val error = validate(graph, "idea").single()
    assertThat(error.unexpectedModules.keys).containsExactlyInAnyOrder(ContentModuleName(CLIENT), ContentModuleName(BASE))
    assertThat(error.unexpectedModules.getValue(ContentModuleName(BASE))).containsExactly("consumer", CLIENT, BASE)
    assertThat(error.errorId()).isEqualTo("rd-client-loading:idea")
    assertThat(error.format(AnsiStyle(useAnsi = false))).contains(
      "Product 'idea'",
      "consumer -> $CLIENT -> $BASE",
      "a .frontend.split module without a dependency on intellij.platform.frontend.split",
      "eligible to load in a monolith IDE",
    )
  }

  @Test
  fun `excluded consumers do not activate their dependencies`() {
    val graph = pluginGraph {
      rdProduct("idea")
      product("idea") { content("consumer") }
      linkContentModuleDeps("consumer", CLIENT, "missing")
      linkContentModuleDeps(CLIENT, BASE)
    }
    assertThat(validate(graph, "idea")).isEmpty()
  }

  @Test
  fun `test plugins and other products cannot satisfy dependencies`() {
    val graph = pluginGraph {
      rdProduct("idea")
      product("idea") {
        content("consumer")
        bundlesTestPlugin("test.plugin")
      }
      testPlugin("test.plugin") { testContent("missing") }
      product("another") { content("missing") }
      linkContentModuleDeps("consumer", CLIENT, "missing")
    }
    assertThat(validate(graph, "idea")).isEmpty()
  }

  @Test
  fun `JPS and test edges do not create runtime demand`() {
    val graph = pluginGraph {
      rdProduct("idea")
      product("idea") { content("consumer") }
      moduleWithDeps("consumer", CLIENT)
      linkContentModuleTestDeps("consumer", CLIENT)
    }
    assertThat(validate(graph, "idea")).isEmpty()
  }

  @Test
  fun `nested module sets and bundled plugin consumers participate`() {
    val graph = pluginGraph {
      product("idea") {
        includesModuleSet("outer")
        bundlesPlugin("consumer.plugin")
      }
      moduleSet("outer") {
        nestedSet("inner") { module(CLIENT, ModuleLoadingRuleValue.ON_DEMAND) }
      }
      plugin("consumer.plugin") { dependsOnContentModule(CLIENT) }
    }
    assertThat(validate(graph, "idea").single().unexpectedModules.keys).containsExactly(ContentModuleName(CLIENT))
  }

  @Test
  fun `a failed required module excludes its plugin and other consumers`() {
    val graph = pluginGraph {
      rdProduct("idea")
      product("idea") { bundlesPlugin("consumer.plugin") }
      plugin("consumer.plugin") {
        content("broken", loading = ModuleLoadingRuleValue.REQUIRED)
        content("consumer")
      }
      linkContentModuleDeps("broken", "missing")
      linkContentModuleDeps("consumer", CLIENT)
    }
    assertThat(validate(graph, "idea")).isEmpty()
  }

  @Test
  fun `an absent optional plugin dependency does not exclude a consumer`() {
    val graph = pluginGraph {
      rdProduct("idea")
      product("idea") { bundlesPlugin("consumer.plugin") }
      plugin("consumer.plugin") {
        dependsOnContentModule(CLIENT)
        dependsOnLegacyPlugin("absent", optional = true)
      }
    }
    assertThat(validate(graph, "idea").single().unexpectedModules.keys).containsExactly(ContentModuleName(CLIENT))
  }

  @Test
  fun `an alias does not keep an excluded provider active`() {
    val graph = pluginGraph {
      rdProduct("idea")
      product("idea") {
        bundlesPlugin("consumer.plugin")
        bundlesPlugin("provider.plugin")
      }
      plugin("consumer.plugin") {
        dependsOnPlugin("provider.alias")
        dependsOnContentModule(CLIENT)
      }
      plugin("provider.plugin") { dependsOnContentModule("missing") }
      pluginAlias("idea", "provider.plugin", "provider.alias")
    }
    assertThat(validate(graph, "idea")).isEmpty()
  }

  @Test
  fun `isolated on demand cycles do not activate themselves`() {
    val graph = pluginGraph {
      rdProduct("idea")
      linkContentModuleDeps(CLIENT, BASE)
      linkContentModuleDeps(BASE, CLIENT)
    }
    assertThat(validate(graph, "idea")).isEmpty()
  }

  @TestFactory
  fun `exception products require only the client and base modules`(): List<DynamicTest> {
    return listOf("Rider", "CLion", "JetBrainsClient", "GoLandJetBrainsClient", "RiderJetBrainsClient", "ideaJetBrainsClient").map { name ->
      DynamicTest.dynamicTest(name) {
        val graph = pluginGraph {
          rdProduct(name)
          product(name) { content("consumer") }
          linkContentModuleDeps("consumer", CLIENT, BASE)
        }
        assertThat(validate(graph, name)).isEmpty()
      }
    }
  }

  @TestFactory
  fun `the client and base modules are mandatory in exception products`(): List<DynamicTest> {
    return listOf(CLIENT, BASE).map { removed ->
      DynamicTest.dynamicTest(removed) {
        val graph = pluginGraph {
          rdProduct("CLion")
          product("CLion") { content("consumer") }
          linkContentModuleDeps("consumer", *RD_MODULES.filterNot { it == removed }.toTypedArray())
        }
        val error = validate(graph, "CLion").single()
        assertThat(error.missingModules.keys).containsExactly(ContentModuleName(removed))
        assertThat(error.missingModules.getValue(ContentModuleName(removed))).contains("No active consumer")
      }
    }
  }

  @Test
  fun `a removed required module reports its absence`() {
    val graph = pluginGraph {
      product("Rider") {
        RD_MODULES.filterNot { it == BASE }.forEach { content(it) }
      }
    }
    val error = validate(graph, "Rider").single()
    assertThat(error.missingModules).containsEntry(ContentModuleName(BASE), "The module is absent from this product.")
    assertThat(error.format(AnsiStyle(useAnsi = false))).doesNotContain("a monolith IDE")
  }

  @Test
  fun `exception products can omit other RD client modules`() {
    val graph = pluginGraph {
      for (name in listOf("Rider", "CLion", "JetBrainsClient")) {
        product(name) {
          content(CLIENT)
          content(BASE)
        }
      }
    }
    assertThat(validate(graph, "Rider", "CLion", "JetBrainsClient")).isEmpty()
  }

  @Test
  fun `product overrides replace module set loading rules`() {
    val graph = pluginGraph {
      rdProduct("Rider")
      product("Rider") { RD_MODULES.forEach { content(it, ModuleLoadingRuleValue.REQUIRED) } }
    }
    assertThat(validate(graph, "Rider")).isEmpty()
  }

  @Test
  fun `future matching modules are forbidden in other products`() {
    val graph = pluginGraph {
      product("NewIde") { content("intellij.rd.client.future") }
    }
    assertThat(validate(graph, "NewIde").single().unexpectedModules.keys).containsExactly(ContentModuleName("intellij.rd.client.future"))
  }

  @Test
  fun `synthetic products are not checked`() {
    val graph = pluginGraph {
      product("idea")
      product("synthetic") { content(CLIENT) }
    }
    assertThat(validate(graph, "idea")).isEmpty()
  }

  private fun validate(graph: PluginGraph, vararg products: String): List<RdClientModuleLoadingError> {
    return SharedTaskOwner("RD client validation test").use { owner ->
      val model = testGenerationModel(graph, owner = owner)
      val discovery = model.discovery.copy(products = products.map {
        DiscoveredProduct(it, ProductConfiguration(emptyList(), "test.Properties"), properties = null, spec = null, pluginXmlPath = null)
      })
      val errors = runValidationRule(RdClientModuleLoadingValidator, model.copy(discovery = discovery))
      assertThat(errors).allMatch { it is RdClientModuleLoadingError }
      errors.filterIsInstance<RdClientModuleLoadingError>()
    }
  }
}

private fun TestPluginGraphBuilder.rdProduct(name: String) {
  product(name) { includesModuleSet("rd.common") }
  moduleSet("rd.common") { RD_MODULES.forEach { module(it, ModuleLoadingRuleValue.ON_DEMAND) } }
}

private const val CLIENT = "intellij.rd.client"
private const val BASE = "$CLIENT.base"
private const val DEBUGGER = "$CLIENT.debugger"
private const val INTERNAL = "$CLIENT.internal"
private val RD_MODULES = listOf(CLIENT, BASE, DEBUGGER, INTERNAL)
