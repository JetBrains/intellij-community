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
import org.jetbrains.intellij.build.productLayout.ModuleActivation
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.dependency.TestPluginGraphBuilder
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetSourceLabels
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.productLayout.model.error.RestrictedModuleActivationError
import org.jetbrains.intellij.build.productLayout.model.error.errorId
import org.jetbrains.intellij.build.productLayout.moduleSet
import org.jetbrains.intellij.build.productLayout.productModules
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class RestrictedModuleActivationValidatorTest {
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
    assertThat(error.errorId()).isEqualTo("restricted-module-activation:idea")
    assertThat(error.format(AnsiStyle(useAnsi = false))).contains(
      "Product 'idea'",
      "consumer -> $CLIENT -> $BASE",
      "No module activation of this product or of its plugins allows these modules.",
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
  fun `products with the activation require only the client and base modules`(): List<DynamicTest> {
    return listOf("Rider", "JetBrainsClient", "GoLandJetBrainsClient").map { name ->
      DynamicTest.dynamicTest(name) {
        val graph = pluginGraph {
          rdProduct(name)
          linkContentModuleDeps("consumer", CLIENT, BASE)
        }
        assertThat(validate(graph, name, specs = mapOf(name to rdSpecWithConsumer()))).isEmpty()
      }
    }
  }

  @TestFactory
  fun `the client and base modules are mandatory in products with the activation`(): List<DynamicTest> {
    return listOf(CLIENT, BASE).map { removed ->
      DynamicTest.dynamicTest(removed) {
        val graph = pluginGraph {
          rdProduct("Rider")
          linkContentModuleDeps("consumer", *RD_MODULES.filterNot { it == removed }.toTypedArray())
        }
        val error = validate(graph, "Rider", specs = mapOf("Rider" to rdSpecWithConsumer())).single()
        assertThat(error.missingModules.keys).containsExactly(ContentModuleName(removed))
        assertThat(error.missingModules.getValue(ContentModuleName(removed))).contains("No active consumer")
      }
    }
  }

  @Test
  fun `a removed required module reports its absence`() {
    val graph = pluginGraph { product("Rider") }
    val spec = productModules {
      moduleActivation(RD_ACTIVATION)
      RD_MODULES.filterNot { it == BASE }.forEach { module(it) }
    }
    val error = validate(graph, "Rider", specs = mapOf("Rider" to spec)).single()
    assertThat(error.missingModules).containsEntry(ContentModuleName(BASE), "The module is absent from this product.")
    assertThat(error.format(AnsiStyle(useAnsi = false))).doesNotContain("No module activation")
  }

  @Test
  fun `products with the activation can omit other RD client modules`() {
    val graph = pluginGraph {
      product("Rider")
      product("JetBrainsClient")
    }
    val spec = productModules {
      moduleActivation(RD_ACTIVATION)
      module(CLIENT)
      module(BASE)
    }
    assertThat(validate(graph, "Rider", "JetBrainsClient", specs = mapOf("Rider" to spec, "JetBrainsClient" to spec))).isEmpty()
  }

  @Test
  fun `product overrides replace module set loading rules`() {
    val graph = pluginGraph { rdProduct("Rider") }
    val spec = productModules {
      moduleActivation(RD_ACTIVATION)
      moduleSet(RD_SET) {
        loading(ModuleLoadingRuleValue.REQUIRED, *RD_MODULES.toTypedArray())
      }
    }
    assertThat(validate(graph, "Rider", specs = mapOf("Rider" to spec))).isEmpty()
  }

  @Test
  fun `a restricted module of a product spec is prohibited without an activation`() {
    val graph = pluginGraph {
      product("NewIde")
      linkContentModuleDeps("consumer", FUTURE)
    }
    val spec = productModules {
      onDemandModule(FUTURE, restricted = true)
      module("consumer")
    }
    val error = validate(graph, "NewIde", specs = mapOf("NewIde" to spec)).single()
    assertThat(error.unexpectedModules.keys).containsExactly(ContentModuleName(FUTURE))
  }

  @Test
  fun `an activation does not allow a module outside its allowed set`() {
    val graph = pluginGraph {
      rdProduct("Rider")
      linkContentModuleDeps("consumer", CLIENT, BASE, FUTURE)
    }
    val spec = productModules {
      moduleActivation(RD_ACTIVATION)
      moduleSet(RD_SET)
      onDemandModule(FUTURE, restricted = true)
      module("consumer")
    }
    val error = validate(graph, "Rider", specs = mapOf("Rider" to spec)).single()
    assertThat(error.unexpectedModules.keys).containsExactly(ContentModuleName(FUTURE))
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
      linkContentModuleDeps("php.frontend", CLIENT, FRONTEND_SPLIT)
    }
    assertThat(validate(graph, "idea", compatiblePlugins = mapOf("idea" to listOf("php")))).isEmpty()
  }

  @Test
  fun `the product mode excludes a bundled split module`() {
    val graph = pluginGraph {
      rdProduct("idea")
      product("idea") {
        content(FRONTEND_SPLIT)
        content("php.frontend")
      }
      linkContentModuleDeps("php.frontend", CLIENT, FRONTEND_SPLIT)
    }
    val error = validate(graph, "idea").single()
    assertThat(error.unexpectedModules.getValue(ContentModuleName(CLIENT))).containsExactly("php.frontend", CLIENT)
    assertThat(validate(graph, "idea", productModeExcludedModules = mapOf("idea" to setOf(FRONTEND_SPLIT)))).isEmpty()
  }

  @Test
  fun `the error lists the modules that the product mode excludes`() {
    val graph = pluginGraph {
      rdProduct("idea")
      product("idea") { content("consumer") }
      linkContentModuleDeps("consumer", CLIENT)
    }
    val error = validate(graph, "idea", productModeExcludedModules = mapOf("idea" to setOf(FRONTEND_SPLIT))).single()
    assertThat(error.format(AnsiStyle(useAnsi = false))).contains("The 'monolith' product mode excludes: $FRONTEND_SPLIT.")
  }

  @Test
  fun `CLion requires the client with Radler and prohibits it with Classic`() {
    assertThat(validate(clionGraph(), "CLion", specs = mapOf("CLion" to clionSpec()), grants = RADLER_GRANT)).isEmpty()
  }

  @Test
  fun `IDEA permits RD activation with the C++ plugin and checks other plugins separately`() {
    val graph = pluginGraph {
      rdProduct("idea")
      plugin("intellij.clion.radler") {
        pluginId(RADLER)
        content("cpp")
      }
      plugin("unrelated") { content("unexpected") }
      linkContentModuleDeps("cpp", CLIENT, BASE)
      linkContentModuleDeps("unexpected", CLIENT)
    }
    assertThat(validate(graph, "idea", compatiblePlugins = mapOf("idea" to listOf("intellij.clion.radler")), grants = RADLER_GRANT)).isEmpty()
    val error = validate(
      graph, "idea", compatiblePlugins = mapOf("idea" to listOf("intellij.clion.radler", "unrelated")), grants = RADLER_GRANT,
    ).single()
    assertThat(error.context).isEqualTo("idea")
    assertThat(error.unexpectedModules.getValue(ContentModuleName(CLIENT))).containsExactly("compatible plugins", "unexpected", CLIENT)
  }

  @Test
  fun `IDEA requires RD activation when the C++ plugin is installed`() {
    val graph = pluginGraph {
      rdProduct("idea")
      plugin("intellij.clion.radler") { pluginId(RADLER) }
    }
    val error = validate(graph, "idea", compatiblePlugins = mapOf("idea" to listOf("intellij.clion.radler")), grants = RADLER_GRANT).single()
    assertThat(error.context).isEqualTo("idea (with compatible intellij.clion.radler)")
    assertThat(error.missingModules.keys).containsExactlyInAnyOrder(ContentModuleName(CLIENT), ContentModuleName(BASE))
  }

  @Test
  fun `a plugin without a grant does not permit RD activation`() {
    val graph = pluginGraph {
      rdProduct("idea")
      plugin("intellij.clion.radler") {
        pluginId(RADLER)
        content("cpp")
      }
      linkContentModuleDeps("cpp", CLIENT, BASE)
    }
    val error = validate(graph, "idea", compatiblePlugins = mapOf("idea" to listOf("intellij.clion.radler"))).single()
    assertThat(error.context).isEqualTo("idea")
    assertThat(error.unexpectedModules.keys).containsExactlyInAnyOrder(ContentModuleName(CLIENT), ContentModuleName(BASE))
  }

  @Test
  fun `CLion Classic reports a consumer outside Radler`() {
    val error = validate(clionGraph(), "CLion", specs = mapOf("CLion" to clionSpec(extraConsumer = true)), grants = RADLER_GRANT).single()
    assertThat(error.context).isEqualTo("CLion (with $CLASSIC)")
    assertThat(error.unexpectedModules.keys).contains(ContentModuleName(CLIENT))
  }

  @Test
  fun `eager RD test frameworks activate the client in Classic`() {
    val error = validate(
      clionGraph(), "CLion",
      specs = mapOf("CLion" to clionSpec()),
      grants = RADLER_GRANT,
      testPlugins = mapOf("CLion" to listOf(clionTests(ModuleLoadingRuleValue.OPTIONAL))),
    ).single()
    assertThat(error.context).isEqualTo("CLion (with $CLASSIC and $CLION_TESTS)")
    assertThat(error.unexpectedModules.keys).contains(ContentModuleName(CLIENT), ContentModuleName(BASE))
  }

  @Test
  fun `on demand RD test frameworks load only for enabled Nova tests`() {
    val testPlugins = mapOf("CLion" to listOf(clionTests(ModuleLoadingRuleValue.ON_DEMAND)))
    assertThat(validate(clionGraph(), "CLion", specs = mapOf("CLion" to clionSpec()), grants = RADLER_GRANT, testPlugins = testPlugins)).isEmpty()
  }

  @Test
  fun `test plugins without the opt-in are not checked`() {
    val testPlugins = mapOf("CLion" to listOf(clionTests(ModuleLoadingRuleValue.OPTIONAL, checkModuleActivation = false)))
    assertThat(validate(clionGraph(), "CLion", specs = mapOf("CLion" to clionSpec()), grants = RADLER_GRANT, testPlugins = testPlugins)).isEmpty()
  }

  private fun clionGraph(): PluginGraph {
    return pluginGraph {
      rdProduct("CLion")
      product("CLion") {
        bundlesPlugin(RADLER)
        bundlesPlugin(CLASSIC)
      }
      plugin(RADLER) { content("radler") }
      plugin(CLASSIC) { content("classic") }
      testPlugin(CLION_TESTS)
      linkContentModuleDeps("radler", CLIENT, BASE)
      linkContentModuleDeps("rd.framework", CLIENT)
      linkContentModuleDeps("rider.framework", "rd.framework", CLIENT, BASE)
      linkContentModuleDeps("nova.tests", "radler", "rider.framework")
      linkContentModuleDeps("unexpected", CLIENT)
    }
  }

  private fun clionSpec(extraConsumer: Boolean = false): ProductModulesContentSpec {
    return productModules {
      moduleSet(RD_SET)
      exclusivePlugins(CLASSIC, RADLER)
      if (extraConsumer) module("unexpected")
    }
  }

  private fun clionTests(loading: ModuleLoadingRuleValue, checkModuleActivation: Boolean = true): TestPluginSpec {
    return TestPluginSpec(
      pluginId = PluginId(CLION_TESTS),
      name = "CLion tests",
      pluginXmlPath = "plugin.xml",
      spec = productModules {
        module("rd.framework", loading = loading)
        module("rider.framework", loading = loading)
        module("nova.tests")
      },
      checkModuleActivation = checkModuleActivation,
    )
  }

  private fun validate(
    graph: PluginGraph,
    vararg products: String,
    specs: Map<String, ProductModulesContentSpec> = emptyMap(),
    grants: Map<String, ModuleActivation> = emptyMap(),
    bundledPlugins: Map<String, List<String>> = emptyMap(),
    compatiblePlugins: Map<String, List<String>> = emptyMap(),
    testPlugins: Map<String, List<TestPluginSpec>> = emptyMap(),
    productModeExcludedModules: Map<String, Set<String>> = emptyMap(),
  ): List<RestrictedModuleActivationError> {
    return SharedTaskOwner("Restricted module activation test").use { owner ->
      val model = testGenerationModel(graph, owner = owner)
      val discovery = model.discovery.copy(
        moduleSetsByLabel = mapOf(ModuleSetSourceLabels.COMMUNITY to listOf(RD_SET)),
        products = products.map {
          DiscoveredProduct(
            it, ProductConfiguration(emptyList(), "test.Properties"), properties = null, spec = specs.get(it), pluginXmlPath = null,
            bundledPluginModules = bundledPlugins.get(it).orEmpty().map(::TargetName),
            productModeExcludedModules = productModeExcludedModules.get(it).orEmpty().mapTo(LinkedHashSet(), ::ContentModuleName),
          )
        },
      )
      val config = model.config.copy(
        nonBundledPlugins = compatiblePlugins.mapValues { (_, plugins) -> plugins.mapTo(LinkedHashSet(), ::TargetName) },
        pluginModuleActivations = grants.mapKeys { PluginId(it.key) },
      )
      val errors = runValidationRule(RestrictedModuleActivationValidator, model.copy(discovery = discovery, config = config, dslTestPluginsByProduct = testPlugins))
      assertThat(errors).allMatch { it is RestrictedModuleActivationError }
      errors.filterIsInstance<RestrictedModuleActivationError>()
    }
  }
}

private fun TestPluginGraphBuilder.rdProduct(name: String) {
  product(name) { includesModuleSet("rd.common") }
  moduleSet("rd.common") { RD_MODULES.forEach { module(it, ModuleLoadingRuleValue.ON_DEMAND) } }
}

private fun rdSpecWithConsumer(): ProductModulesContentSpec {
  return productModules {
    moduleActivation(RD_ACTIVATION)
    moduleSet(RD_SET)
    module("consumer")
  }
}

private const val CLIENT = "intellij.rd.client"
private const val BASE = "$CLIENT.base"
private const val DEBUGGER = "$CLIENT.debugger"
private const val INTERNAL = "$CLIENT.internal"
private const val FUTURE = "intellij.future.restricted"
private const val FRONTEND_SPLIT = "intellij.platform.frontend.split"
private const val RADLER = "org.jetbrains.plugins.clion.radler"
private const val CLASSIC = "com.intellij.cidr.lang"
private const val CLION_TESTS = "intellij.clion.dev.build.plugin"
private val RD_MODULES = listOf(CLIENT, BASE, DEBUGGER, INTERNAL)
private val RD_SET = moduleSet("rd.common") { RD_MODULES.forEach { onDemandModule(it, restricted = true) } }
private val RD_ACTIVATION = ModuleActivation.create(required = listOf(CLIENT, BASE), allowed = listOf(DEBUGGER, INTERNAL))
private val RADLER_GRANT = mapOf(RADLER to RD_ACTIVATION)
