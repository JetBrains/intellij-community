// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.buildScripts.concurrency.SharedTaskOwner
import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.dependency.TestPluginGraphBuilder
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.productLayout.model.error.RdClientModuleLoadingError
import org.jetbrains.intellij.build.productLayout.model.error.errorId
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle
import org.jetbrains.intellij.build.productLayout.productModules
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
    return listOf("Rider", "JetBrainsClient", "GoLandJetBrainsClient", "RiderJetBrainsClient", "ideaJetBrainsClient").map { name ->
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
          rdProduct("Rider")
          product("Rider") { content("consumer") }
          linkContentModuleDeps("consumer", *RD_MODULES.filterNot { it == removed }.toTypedArray())
        }
        val error = validate(graph, "Rider").single()
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
      for (name in listOf("Rider", "JetBrainsClient")) {
        product(name) {
          content(CLIENT)
          content(BASE)
        }
      }
    }
    assertThat(validate(graph, "Rider", "JetBrainsClient")).isEmpty()
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

  @Test
  fun `a plugin from the product declaration activates the client`() {
    val graph = pluginGraph {
      rdProduct("PhpStorm")
      plugin("php") { content("php.frontend") }
      linkContentModuleDeps("php.frontend", CLIENT)
    }
    val error = validate(graph, "PhpStorm", bundledPlugins = mapOf("PhpStorm" to listOf("php"))).single()
    assertThat(error.unexpectedModules.getValue(ContentModuleName(CLIENT))).containsExactly("php.frontend", CLIENT)
  }

  @Test
  fun `compatible plugins can satisfy each other without changing the baseline`() {
    val graph = pluginGraph {
      rdProduct("idea")
      plugin("consumer") {
        dependsOnPlugin("provider")
        content("frontend")
      }
      plugin("provider")
      linkContentModuleDeps("frontend", CLIENT)
    }
    assertThat(validate(graph, "idea")).isEmpty()
    assertThat(validate(graph, "idea", compatiblePlugins = mapOf("other" to listOf("consumer", "provider")))).isEmpty()
    assertThat(validate(graph, "idea", compatiblePlugins = mapOf("idea" to listOf("consumer")))).isEmpty()
    val error = validate(graph, "idea", compatiblePlugins = mapOf("idea" to listOf("consumer", "provider"))).single()
    assertThat(error.unexpectedModules.getValue(ContentModuleName(CLIENT))).containsExactly("compatible plugins", "frontend", CLIENT)
  }

  @Test
  fun `compatible plugins do not replace a baseline activation path`() {
    val graph = pluginGraph {
      rdProduct("idea")
      product("idea") { content("baseline") }
      plugin("consumer") { dependsOnContentModule(CLIENT) }
      linkContentModuleDeps("baseline", CLIENT)
    }
    val error = validate(graph, "idea", compatiblePlugins = mapOf("idea" to listOf("consumer"))).single()
    assertThat(error.unexpectedModules.getValue(ContentModuleName(CLIENT))).containsExactly("baseline", CLIENT)
  }

  @Test
  fun `a split dependency excludes a compatible frontend plugin module`() {
    val graph = pluginGraph {
      rdProduct("idea")
      plugin("php") { content("php.frontend") }
      linkContentModuleDeps("php.frontend", CLIENT, "intellij.platform.frontend.split")
    }
    assertThat(validate(graph, "idea", compatiblePlugins = mapOf("idea" to listOf("php")))).isEmpty()
  }

  @Test
  fun `CLion requires the client in Nova and excludes it in Classic`() {
    assertThat(validate(clionGraph(), "CLion")).isEmpty()
  }

  @Test
  fun `IDEA permits RD activation with the C++ plugin and checks other plugins separately`() {
    val graph = pluginGraph {
      rdProduct("idea")
      plugin("intellij.clion.radler") {
        pluginId("org.jetbrains.plugins.clion.radler")
        content("cpp")
      }
      plugin("unrelated") { content("unexpected") }
      linkContentModuleDeps("cpp", CLIENT, BASE)
      linkContentModuleDeps("unexpected", CLIENT)
    }
    assertThat(validate(graph, "idea", compatiblePlugins = mapOf("idea" to listOf("intellij.clion.radler")))).isEmpty()
    val error = validate(graph, "idea", compatiblePlugins = mapOf("idea" to listOf("intellij.clion.radler", "unrelated"))).single()
    assertThat(error.context).isEqualTo("idea")
    assertThat(error.unexpectedModules.getValue(ContentModuleName(CLIENT))).containsExactly("compatible plugins", "unexpected", CLIENT)
  }

  @Test
  fun `IDEA requires RD activation when the C++ plugin is installed`() {
    val graph = pluginGraph {
      rdProduct("idea")
      plugin("intellij.clion.radler") { pluginId("org.jetbrains.plugins.clion.radler") }
    }
    val error = validate(graph, "idea", compatiblePlugins = mapOf("idea" to listOf("intellij.clion.radler"))).single()
    assertThat(error.context).isEqualTo("idea (C++ plugin)")
    assertThat(error.missingModules.keys).containsExactlyInAnyOrder(ContentModuleName(CLIENT), ContentModuleName(BASE))
  }

  @Test
  fun `CLion Classic reports a consumer outside Radler`() {
    val error = validate(clionGraph(extraConsumer = true), "CLion").single()
    assertThat(error.context).isEqualTo("CLion (Classic)")
    assertThat(error.unexpectedModules.keys).contains(ContentModuleName(CLIENT))
  }

  @Test
  fun `eager RD test frameworks activate the client in Classic`() {
    val error = validate(clionGraph(), "CLion", testPlugins = mapOf("CLion" to listOf(clionTests(ModuleLoadingRuleValue.OPTIONAL)))).single()
    assertThat(error.context).isEqualTo("CLion (Classic tests)")
    assertThat(error.unexpectedModules.keys).contains(ContentModuleName(CLIENT), ContentModuleName(BASE))
  }

  @Test
  fun `on demand RD test frameworks load only for enabled Nova tests`() {
    assertThat(validate(clionGraph(), "CLion", testPlugins = mapOf("CLion" to listOf(clionTests(ModuleLoadingRuleValue.ON_DEMAND))))).isEmpty()
  }

  private fun clionGraph(extraConsumer: Boolean = false): PluginGraph {
    return pluginGraph {
      rdProduct("CLion")
      product("CLion") {
        bundlesPlugin("org.jetbrains.plugins.clion.radler")
        bundlesPlugin("com.intellij.cidr.lang")
        if (extraConsumer) content("unexpected")
      }
      plugin("org.jetbrains.plugins.clion.radler") { content("radler") }
      plugin("com.intellij.cidr.lang") { content("classic") }
      testPlugin("intellij.clion.dev.build.plugin")
      linkContentModuleDeps("radler", CLIENT, BASE)
      linkContentModuleDeps("rd.framework", CLIENT)
      linkContentModuleDeps("rider.framework", "rd.framework", CLIENT, BASE)
      linkContentModuleDeps("nova.tests", "radler", "rider.framework")
      if (extraConsumer) linkContentModuleDeps("unexpected", CLIENT)
    }
  }

  private fun clionTests(loading: ModuleLoadingRuleValue): TestPluginSpec {
    return TestPluginSpec(
      pluginId = PluginId("intellij.clion.dev.build.plugin"),
      name = "CLion tests",
      pluginXmlPath = "plugin.xml",
      spec = productModules {
        module("rd.framework", loading = loading)
        module("rider.framework", loading = loading)
        module("nova.tests")
      },
    )
  }

  private fun validate(
    graph: PluginGraph,
    vararg products: String,
    bundledPlugins: Map<String, List<String>> = emptyMap(),
    compatiblePlugins: Map<String, List<String>> = emptyMap(),
    testPlugins: Map<String, List<TestPluginSpec>> = emptyMap(),
  ): List<RdClientModuleLoadingError> {
    return SharedTaskOwner("RD client validation test").use { owner ->
      val model = testGenerationModel(graph, owner = owner)
      val discovery = model.discovery.copy(products = products.map {
        DiscoveredProduct(
          it, ProductConfiguration(emptyList(), "test.Properties"), properties = null, spec = null, pluginXmlPath = null,
          bundledPluginModules = bundledPlugins.get(it).orEmpty().map(::TargetName),
        )
      })
      val config = model.config.copy(nonBundledPlugins = compatiblePlugins.mapValues { (_, plugins) -> plugins.mapTo(LinkedHashSet(), ::TargetName) })
      val errors = runValidationRule(RdClientModuleLoadingValidator, model.copy(discovery = discovery, config = config, dslTestPluginsByProduct = testPlugins))
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
