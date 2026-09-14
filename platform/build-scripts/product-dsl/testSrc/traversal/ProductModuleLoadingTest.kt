// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.traversal

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.PluginModuleId
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.deps.PluginDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.deps.TestPluginDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.TestPluginDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.discovery.ContentModuleInfo
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.discovery.PluginSource
import org.jetbrains.intellij.build.productLayout.moduleSet
import org.jetbrains.intellij.build.productLayout.productModules
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class ProductModuleLoadingTest {
  @Test
  fun `a modular loader plugin activates content without changing the graph`() {
    val graph = pluginGraph {
      product("IDE") { content("on.demand", loading = ModuleLoadingRuleValue.ON_DEMAND) }
      plugin("modular.plugin") { dependsOnContentModule("on.demand") }
    }
    assertThat(analyze(graph, modularPlugins = listOf("modular.plugin")).activationPaths.keys)
      .containsExactly(ContentModuleName("on.demand"))
    graph.query {
      val bundled = ArrayList<String>()
      requireNotNull(product("IDE")).bundles { bundled.add(it.name().value) }
      assertThat(bundled).isEmpty()
    }
  }

  @Test
  fun `a modular loader plugin can supply descriptors absent from the graph`() {
    val graph = pluginGraph { product("IDE") { content("on.demand", loading = ModuleLoadingRuleValue.ON_DEMAND) } }
    val info = PluginContentInfo(
      pluginXmlPath = Path.of("plugin.xml"),
      pluginXmlContent = "<idea-plugin/>",
      pluginId = PluginId("modular.plugin"),
      contentModules = listOf(ContentModuleInfo(PluginModuleId("modular.consumer", "jetbrains"), ModuleLoadingRuleValue.OPTIONAL)),
    )
    val result = analyze(
      graph,
      descriptors = mapOf("modular.consumer" to descriptor().copy(existingModuleDependencies = listOf("on.demand"))),
      modularPlugins = listOf("modular.plugin"),
      pluginDescriptors = mapOf("modular.plugin" to info),
    )
    assertThat(result.activationPaths.getValue(ContentModuleName("on.demand")))
      .containsExactly("modular.consumer", "on.demand")
  }

  @Test
  fun `the product applies loading overrides to a module set`() {
    val graph = pluginGraph {
      product("IDE") { includesModuleSet("shared") }
      moduleSet("shared") { module("on.demand", ModuleLoadingRuleValue.ON_DEMAND) }
    }
    val shared = moduleSet("shared") { module("on.demand", ModuleLoadingRuleValue.ON_DEMAND) }
    val spec = productModules {
      moduleSet(shared) { loading(ModuleLoadingRuleValue.REQUIRED, "on.demand") }
    }
    assertThat(analyze(graph, spec = spec).activationPaths.keys).containsExactly(ContentModuleName("on.demand"))
  }

  @Test
  fun `a product alias resolves even when another product supplies it through a plugin`() {
    val graph = pluginGraph {
      product("IDE") { bundlesPlugin("consumer") }
      plugin("consumer") { dependsOnPlugin("product.capability") }
      plugin("other.provider")
      pluginAlias("IDE", "other.provider", "product.capability")
      plugin("consumer") { content("active.module") }
    }
    val spec = productModules { alias("product.capability") }
    assertThat(analyze(graph, spec = spec).activationPaths.keys).containsExactly(ContentModuleName("active.module"))
  }

  @Test
  fun `private plugin content cannot satisfy an external consumer`() {
    val graph = pluginGraph {
      product("IDE") {
        content("consumer")
        bundlesPlugin("provider")
      }
      plugin("provider") { content("private.module", loading = ModuleLoadingRuleValue.ON_DEMAND) }
      linkContentModuleDeps("consumer", "private.module")
    }
    val result = analyze(graph, descriptors = mapOf("private.module" to descriptor(visibility = ModuleVisibilityValue.PRIVATE)))
    assertThat(result.activationPaths).isEmpty()
    assertThat(result.exclusions.getValue(ContentModuleName("consumer"))).contains("not visible")
  }

  @Test
  fun `private plugin content satisfies a consumer in the same plugin`() {
    val graph = pluginGraph {
      product("IDE") { bundlesPlugin("provider") }
      plugin("provider") {
        content("consumer")
        content("private.module", loading = ModuleLoadingRuleValue.ON_DEMAND)
      }
      linkContentModuleDeps("consumer", "private.module")
    }
    val result = analyze(graph, descriptors = mapOf("private.module" to descriptor(visibility = ModuleVisibilityValue.PRIVATE)))
    assertThat(result.activationPaths.keys).containsExactlyInAnyOrder(ContentModuleName("consumer"), ContentModuleName("private.module"))
  }

  @Test
  fun `a visible copy satisfies a dependency when a private copy also exists`() {
    val graph = pluginGraph {
      product("IDE") {
        content("consumer")
        content("shared")
        bundlesPlugin("provider")
      }
      plugin("provider") { content("shared", loading = ModuleLoadingRuleValue.ON_DEMAND) }
      linkContentModuleDeps("consumer", "shared")
    }
    val result = analyze(graph, descriptors = mapOf("shared" to descriptor(visibility = ModuleVisibilityValue.PRIVATE)))
    assertThat(result.activationPaths).containsKey(ContentModuleName("consumer"))
  }

  @Test
  fun `a content module alias creates demand for its provider`() {
    val graph = pluginGraph {
      product("IDE") {
        content("provider.module", loading = ModuleLoadingRuleValue.ON_DEMAND)
        bundlesPlugin("consumer.plugin")
      }
      plugin("consumer.plugin") { dependsOnPlugin("provider.alias") }
    }
    val result = analyze(graph, descriptors = mapOf("provider.module" to descriptor(aliases = listOf("provider.alias"))))
    assertThat(result.activationPaths.getValue(ContentModuleName("provider.module")))
      .containsExactly("plugin consumer.plugin", "provider.module")
  }

  @Test
  fun `a missing plugin dependency excludes its content module consumer`() {
    val graph = pluginGraph {
      product("IDE") {
        content("consumer")
        content("on.demand", loading = ModuleLoadingRuleValue.ON_DEMAND)
      }
      linkContentModuleDeps("consumer", "on.demand")
    }
    val result = analyze(graph, descriptors = mapOf("consumer" to descriptor(pluginDependencies = listOf("absent.plugin"))))
    assertThat(result.activationPaths).isEmpty()
    assertThat(result.exclusions.getValue(ContentModuleName("consumer"))).contains("absent.plugin")
  }

  @Test
  fun `a dependency omitted by the plan does not activate a module`() {
    val graph = pluginGraph {
      product("IDE") {
        content("consumer")
        content("on.demand", loading = ModuleLoadingRuleValue.ON_DEMAND)
      }
      linkContentModuleDeps("consumer", "on.demand")
    }
    val result = analyze(graph, plans = listOf(plan("consumer")))
    assertThat(result.activationPaths.keys).containsExactly(ContentModuleName("consumer"))
  }

  @Test
  fun `a suppressed dependency preserved in XML still activates a module`() {
    val graph = pluginGraph {
      product("IDE") {
        content("consumer")
        content("on.demand", loading = ModuleLoadingRuleValue.ON_DEMAND)
      }
    }
    val plan = plan("consumer").copy(
      existingXmlModuleDependencies = setOf(ContentModuleName("on.demand")),
      suppressedModules = setOf(ContentModuleName("on.demand")),
    )
    val result = analyze(graph, plans = listOf(plan))
    assertThat(result.activationPaths.getValue(ContentModuleName("on.demand"))).containsExactly("consumer", "on.demand")
  }

  @Test
  fun `a plugin dependency omitted by the plan does not exclude a module`() {
    val graph = pluginGraph { product("IDE") { content("consumer") } }
    val result = analyze(
      graph,
      descriptors = mapOf("consumer" to descriptor(pluginDependencies = listOf("absent.plugin"))),
      plans = listOf(plan("consumer")),
    )
    assertThat(result.activationPaths.keys).containsExactly(ContentModuleName("consumer"))
  }

  @Test
  fun `a planned plugin dependency must resolve in the product`() {
    val graph = pluginGraph { product("IDE") { content("consumer") } }
    val result = analyze(graph, plans = listOf(plan("consumer").copy(writtenPluginDependencies = listOf(PluginId("absent.plugin")))))
    assertThat(result.activationPaths).isEmpty()
  }

  @Test
  fun `a disabled plugin cannot satisfy an alias dependency`() {
    val graph = pluginGraph {
      product("IDE") {
        bundlesPlugin("provider")
        bundlesPlugin("consumer")
      }
      plugin("provider")
      pluginAlias("IDE", "provider", "capability")
      plugin("consumer") {
        dependsOnPlugin("capability")
        content("consumer.module")
      }
    }
    assertThat(analyze(graph).activationPaths.keys).containsExactly(ContentModuleName("consumer.module"))
    assertThat(analyze(graph, disabledPluginIds = setOf(PluginId("provider"))).activationPaths).isEmpty()
  }

  @Test
  fun `descriptor plugins respect disabled IDs and exclude test plugins`() {
    val graph = pluginGraph { product("IDE") }
    val info = PluginContentInfo(
      pluginXmlPath = Path.of("plugin.xml"),
      pluginXmlContent = "<idea-plugin/>",
      pluginId = PluginId("provider.id"),
      contentModules = listOf(ContentModuleInfo(PluginModuleId("consumer", "jetbrains"), ModuleLoadingRuleValue.OPTIONAL)),
    )
    assertThat(analyze(graph, modularPlugins = listOf("provider.target"), pluginDescriptors = mapOf("provider.target" to info)).activationPaths)
      .containsKey(ContentModuleName("consumer"))
    assertThat(analyze(
      graph, modularPlugins = listOf("provider.target"), pluginDescriptors = mapOf("provider.target" to info),
      disabledPluginIds = setOf(PluginId("provider.id")),
    ).activationPaths).isEmpty()
    assertThat(analyze(
      graph, modularPlugins = listOf("provider.target"),
      pluginDescriptors = mapOf("provider.target" to info.copy(source = PluginSource.TEST)),
    ).activationPaths).isEmpty()
  }

  @Test
  fun `selected test content uses expanded loading rules and additional bundles`() {
    val graph = pluginGraph {
      product("IDE") { bundlesTestPlugin("tests") }
      testPlugin("tests") { testContent("stale.content") }
      plugin("provider") { content("production") }
      linkContentModuleDeps("tests.consumer", "framework", "production")
    }
    val spec = TestPluginSpec(
      pluginId = PluginId("tests"), name = "Tests", pluginXmlPath = "plugin.xml",
      additionalBundledPluginTargetNames = listOf(TargetName("provider")),
      spec = productModules {
        moduleSet(moduleSet("expanded") {
          module("framework", loading = ModuleLoadingRuleValue.ON_DEMAND)
          module("unused.framework", loading = ModuleLoadingRuleValue.ON_DEMAND)
        })
        module("tests.consumer")
      },
    )
    assertThat(analyze(graph, modularPlugins = listOf("tests")).activationPaths).isEmpty()
    val result = analyze(graph, testPlugins = listOf(spec))
    assertThat(result.activationPaths.keys).containsExactlyInAnyOrder(
      ContentModuleName("production"), ContentModuleName("tests.consumer"), ContentModuleName("framework"),
    )
    assertThat(result.activationPaths.getValue(ContentModuleName("framework"))).containsExactly("tests.consumer", "framework")
  }

  @Test
  fun `test root dependencies use the effective plan and the platform module`() {
    val graph = pluginGraph {
      product("IDE") { content("platform", loading = ModuleLoadingRuleValue.ON_DEMAND) }
      testPlugin("tests") { dependsOnContentModule("stale.dependency") }
    }
    val spec = TestPluginSpec(
      pluginId = PluginId("tests"), name = "Tests", pluginXmlPath = "plugin.xml", platformModule = "platform",
      spec = productModules { module("framework", loading = ModuleLoadingRuleValue.ON_DEMAND) },
    )
    val plan = TestPluginDependencyPlan(
      spec = spec, productName = "IDE", productClass = "Properties",
      pluginDependencies = emptyList(), moduleDependencies = listOf(ContentModuleName("framework")),
      requiredByPlugin = emptyMap(), unresolvedDependencies = emptyList(),
    )
    assertThat(analyze(graph, testPlugins = listOf(spec), testPlans = listOf(plan)).activationPaths.keys)
      .containsExactly(ContentModuleName("platform"))
    assertThat(analyze(graph, testPlugins = listOf(spec.copy(platformModule = "absent")), testPlans = listOf(plan)).activationPaths).isEmpty()
    assertThat(analyze(
      graph, testPlugins = listOf(spec), testPlans = listOf(plan.copy(pluginDependencies = listOf(PluginId("absent")))),
    ).activationPaths).isEmpty()
    assertThat(analyze(
      graph, testPlugins = listOf(spec), testPlans = listOf(plan.copy(moduleDependencies = listOf(ContentModuleName("absent")))),
    ).activationPaths).isEmpty()
  }

  private fun analyze(
    graph: PluginGraph,
    descriptors: Map<String, ModuleDescriptorCache.DescriptorInfo> = emptyMap(),
    plans: List<ContentModuleDependencyPlan> = emptyList(),
    spec: ProductModulesContentSpec? = null,
    modularPlugins: List<String> = emptyList(),
    pluginDescriptors: Map<String, PluginContentInfo> = emptyMap(),
    disabledPluginIds: Set<PluginId> = emptySet(),
    testPlugins: List<TestPluginSpec> = emptyList(),
    testPlans: List<TestPluginDependencyPlan> = emptyList(),
  ): ProductModuleLoadingResult {
    return ProductModuleLoading(
      graph,
      ContentModuleDependencyPlanOutput(plans),
      PluginDependencyPlanOutput(emptyList()),
      descriptorLookup = { descriptors.get(it.value) },
      pluginLookup = { pluginDescriptors.get(it.value) },
      testPluginPlans = TestPluginDependencyPlanOutput(testPlans),
    ).analyze("IDE", spec, modularPlugins.map(::TargetName), disabledPluginIds, testPlugins)
  }

  private fun descriptor(
    visibility: ModuleVisibilityValue = ModuleVisibilityValue.PUBLIC,
    aliases: List<String> = emptyList(),
    pluginDependencies: List<String> = emptyList(),
  ): ModuleDescriptorCache.DescriptorInfo {
    return ModuleDescriptorCache.DescriptorInfo(
      descriptorPath = Path.of("module.xml"),
      content = "<idea-plugin/>",
      skipDependencyGeneration = false,
      moduleVisibility = visibility,
      pluginAliases = aliases,
      existingPluginDependencies = pluginDependencies,
    )
  }

  private fun plan(name: String): ContentModuleDependencyPlan {
    return ContentModuleDependencyPlan(
      contentModuleName = ContentModuleName(name),
      descriptorPath = Path.of("$name.xml"),
      descriptorContent = "<idea-plugin/>",
      moduleDependencies = emptyList(),
      pluginDependencies = emptyList(),
      testDependencies = emptyList(),
      existingXmlModuleDependencies = emptySet(),
      existingXmlPluginDependencies = emptySet(),
      preserveExistingPluginDependencies = emptySet(),
      writtenPluginDependencies = emptyList(),
      requiredPluginDependencies = emptySet(),
      suppressedModules = emptySet(),
      suppressedPlugins = emptySet(),
      suppressionUsages = emptyList(),
    )
  }
}
