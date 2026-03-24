// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.PluginId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.productLayout.moduleSet
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContextImpl
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.productModules
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class TestPluginXmlGeneratorTest {
  @Test
  fun `generates dependencies for DSL test plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("TestProduct") { bundlesPlugin("intellij.target.plugin") }
      testPlugin("intellij.consumer.test.plugin") {
        pluginId("intellij.consumer.test.plugin")
        content("intellij.consumer.module")
      }
      plugin("intellij.target.plugin") {
        pluginId("intellij.target.plugin")
      }
      target("intellij.consumer.test.plugin") {
        dependsOn("intellij.target.plugin")
      }
      linkPluginMainTarget("intellij.consumer.test.plugin")
    }

    val spec = TestPluginSpec(
      pluginId = PluginId("intellij.consumer.test.plugin"),
      name = "Consumer Test Plugin",
      pluginXmlPath = "test-plugin/META-INF/plugin.xml",
      spec = productModules {
        requiredModule("intellij.consumer.module")
      }
    )

    val fileUpdater = DeferredFileUpdater(tempDir)
    val baseModel = testGenerationModel(graph, fileUpdater = fileUpdater)
    val discovery = baseModel.discovery.copy(
      products = listOf(
        DiscoveredProduct(
          name = "TestProduct",
          config = ProductConfiguration(modules = emptyList(), className = "TestProduct"),
          properties = null,
          spec = null,
          pluginXmlPath = null,
        )
      )
    )
    val model = baseModel.copy(
      discovery = discovery,
      projectRoot = tempDir,
      fileUpdater = fileUpdater,
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec)),
    )

    val ctx = ComputeContextImpl(model)
    runPlannerAndGenerator(ctx)

    val diffs = fileUpdater.getDiffs()
    assertThat(diffs).hasSize(1)
    val xml = diffs.single().expectedContent
    assertThat(xml).contains("<dependencies>")
    assertThat(xml).contains("<plugin id=\"intellij.target.plugin\"/>")
    assertThat(xml).doesNotContain("Generated dependencies - run `Generate Product Layouts` to regenerate")
    }
  }

  @Test
  fun `skips unresolvable plugin dependency in DSL test plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      testPlugin("intellij.consumer.test.plugin") {
        pluginId("intellij.consumer.test.plugin")
        content("intellij.consumer.module")
      }
      plugin("intellij.target.plugin") {
        pluginId("intellij.target.plugin")
      }
      target("intellij.consumer.test.plugin") {
        dependsOn("intellij.target.plugin")
      }
      linkPluginMainTarget("intellij.consumer.test.plugin")
    }

    val spec = TestPluginSpec(
      pluginId = PluginId("intellij.consumer.test.plugin"),
      name = "Consumer Test Plugin",
      pluginXmlPath = "test-plugin/META-INF/plugin.xml",
      spec = productModules {
        requiredModule("intellij.consumer.module")
      }
    )

    val fileUpdater = DeferredFileUpdater(tempDir)
    val baseModel = testGenerationModel(graph, fileUpdater = fileUpdater)
    val discovery = baseModel.discovery.copy(
      products = listOf(
        DiscoveredProduct(
          name = "TestProduct",
          config = ProductConfiguration(modules = emptyList(), className = "TestProduct"),
          properties = null,
          spec = null,
          pluginXmlPath = null,
        )
      )
    )
    val model = baseModel.copy(
      discovery = discovery,
      projectRoot = tempDir,
      fileUpdater = fileUpdater,
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec)),
    )

    val ctx = ComputeContextImpl(model)
    runPlannerAndGenerator(ctx)

    val diffs = fileUpdater.getDiffs()
    assertThat(diffs).hasSize(1)
    val xml = diffs.single().expectedContent
    assertThat(xml).doesNotContain("<plugin id=\"intellij.target.plugin\"/>")
    assertThat(xml).doesNotContain("<dependencies>")

    val errors = ctx.getNodeErrors(TestPluginXmlGenerator.id)
    assertThat(errors).isEmpty()
    }
  }

  @Test
  fun `keeps module dependency when target is not globally embedded across products`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("Idea") {
      }
      product("JetBrainsClient") {
        includesModuleSet("client.set")
      }
      moduleSet("client.set") {
        module("intellij.platform.frontend.split", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
      }
      testPlugin("intellij.consumer.test.plugin") {
        pluginId("intellij.consumer.test.plugin")
        content("intellij.consumer.module")
      }
      target("intellij.consumer.test.plugin") {
        dependsOn("intellij.platform.frontend.split")
      }
      linkPluginMainTarget("intellij.consumer.test.plugin")
    }

    val spec = TestPluginSpec(
      pluginId = PluginId("intellij.consumer.test.plugin"),
      name = "Consumer Test Plugin",
      pluginXmlPath = "test-plugin/META-INF/plugin.xml",
      spec = productModules {
        requiredModule("intellij.consumer.module")
      }
    )

    val fileUpdater = DeferredFileUpdater(tempDir)
    val baseModel = testGenerationModel(graph, fileUpdater = fileUpdater)
    val discovery = baseModel.discovery.copy(
      products = listOf(
        DiscoveredProduct(
          name = "Idea",
          config = ProductConfiguration(modules = emptyList(), className = "Idea"),
          properties = null,
          spec = null,
          pluginXmlPath = null,
        ),
        DiscoveredProduct(
          name = "JetBrainsClient",
          config = ProductConfiguration(modules = emptyList(), className = "JetBrainsClient"),
          properties = null,
          spec = null,
          pluginXmlPath = null,
        ),
      )
    )
    val model = baseModel.copy(
      discovery = discovery,
      projectRoot = tempDir,
      fileUpdater = fileUpdater,
      dslTestPluginsByProduct = mapOf("Idea" to listOf(spec)),
    )

    val ctx = ComputeContextImpl(model)
    runPlannerAndGenerator(ctx)

    val diffs = fileUpdater.getDiffs()
    assertThat(diffs).hasSize(1)
    val xml = diffs.single().expectedContent
    assertThat(xml).contains("<dependencies>")
    assertThat(xml).contains("<module name=\"intellij.platform.frontend.split\"/>")

    val errors = ctx.getNodeErrors(TestPluginXmlGenerator.id)
    assertThat(errors).isEmpty()
    }
  }

  @Test
  fun `library content dependencies stay module dependencies in DSL test plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("TestProduct") { bundlesPlugin("intellij.owner.plugin") }
      plugin("intellij.owner.plugin") {
        pluginId("intellij.owner.plugin")
        content("intellij.libraries.testng")
      }
      testPlugin("intellij.consumer.test.plugin") {
        pluginId("intellij.consumer.test.plugin")
        content("intellij.consumer.module")
      }
      target("intellij.consumer.test.plugin") {
        dependsOn("intellij.libraries.testng")
      }
      linkPluginMainTarget("intellij.consumer.test.plugin")
    }

    val spec = TestPluginSpec(
      pluginId = PluginId("intellij.consumer.test.plugin"),
      name = "Consumer Test Plugin",
      pluginXmlPath = "test-plugin/META-INF/plugin.xml",
      spec = productModules {
        requiredModule("intellij.consumer.module")
      }
    )

    val fileUpdater = DeferredFileUpdater(tempDir)
    val baseModel = testGenerationModel(graph, fileUpdater = fileUpdater)
    val discovery = baseModel.discovery.copy(
      products = listOf(
        DiscoveredProduct(
          name = "TestProduct",
          config = ProductConfiguration(modules = emptyList(), className = "TestProduct"),
          properties = null,
          spec = null,
          pluginXmlPath = null,
        )
      )
    )
    val model = baseModel.copy(
      discovery = discovery,
      projectRoot = tempDir,
      fileUpdater = fileUpdater,
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec)),
    )

    val ctx = ComputeContextImpl(model)
    runPlannerAndGenerator(ctx)

    val diffs = fileUpdater.getDiffs()
    assertThat(diffs).hasSize(1)
    val xml = diffs.single().expectedContent
    assertThat(xml).contains("<dependencies>")
    assertThat(xml).contains("<module name=\"intellij.libraries.testng\"/>")
    assertThat(xml).doesNotContain("<plugin id=\"intellij.owner.plugin\"/>")

    val errors = ctx.getNodeErrors(TestPluginXmlGenerator.id)
    assertThat(errors).isEmpty()
    }
  }

  @Test
  fun `plugin-owned content dependency becomes plugin dependency in DSL test plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("TestProduct") { bundlesPlugin("intellij.owner.plugin") }
      plugin("intellij.owner.plugin") {
        pluginId("intellij.owner.plugin")
        content("intellij.owner.module")
      }
      testPlugin("intellij.consumer.test.plugin") {
        pluginId("intellij.consumer.test.plugin")
        content("intellij.consumer.module")
      }
      target("intellij.consumer.test.plugin") {
        dependsOn("intellij.owner.module")
      }
      linkPluginMainTarget("intellij.consumer.test.plugin")
    }

    val spec = TestPluginSpec(
      pluginId = PluginId("intellij.consumer.test.plugin"),
      name = "Consumer Test Plugin",
      pluginXmlPath = "test-plugin/META-INF/plugin.xml",
      spec = productModules {
        requiredModule("intellij.consumer.module")
      }
    )

    val fileUpdater = DeferredFileUpdater(tempDir)
    val baseModel = testGenerationModel(graph, fileUpdater = fileUpdater)
    val discovery = baseModel.discovery.copy(
      products = listOf(
        DiscoveredProduct(
          name = "TestProduct",
          config = ProductConfiguration(modules = emptyList(), className = "TestProduct"),
          properties = null,
          spec = null,
          pluginXmlPath = null,
        )
      )
    )
    val model = baseModel.copy(
      discovery = discovery,
      projectRoot = tempDir,
      fileUpdater = fileUpdater,
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec)),
    )

    val ctx = ComputeContextImpl(model)
    runPlannerAndGenerator(ctx)

    val diffs = fileUpdater.getDiffs()
    assertThat(diffs).hasSize(1)
    val xml = diffs.single().expectedContent
    assertThat(xml).contains("<dependencies>")
    assertThat(xml).contains("<plugin id=\"intellij.owner.plugin\"/>")
    assertThat(xml).doesNotContain("<module name=\"intellij.owner.module\"/>")
    }
  }

  @Test
  fun `test-plugin-owned content dependency stays module dependency in DSL test plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      testPlugin("intellij.owner.test.plugin") {
        pluginId("intellij.owner.test.plugin")
        content("intellij.owner.module")
      }
      testPlugin("intellij.consumer.test.plugin") {
        pluginId("intellij.consumer.test.plugin")
        content("intellij.consumer.module")
      }
      target("intellij.consumer.test.plugin") {
        dependsOn("intellij.owner.module")
      }
      linkPluginMainTarget("intellij.consumer.test.plugin")
    }

    val spec = TestPluginSpec(
      pluginId = PluginId("intellij.consumer.test.plugin"),
      name = "Consumer Test Plugin",
      pluginXmlPath = "test-plugin/META-INF/plugin.xml",
      spec = productModules {
        requiredModule("intellij.consumer.module")
      }
    )

    val fileUpdater = DeferredFileUpdater(tempDir)
    val baseModel = testGenerationModel(graph, fileUpdater = fileUpdater)
    val discovery = baseModel.discovery.copy(
      products = listOf(
        DiscoveredProduct(
          name = "TestProduct",
          config = ProductConfiguration(modules = emptyList(), className = "TestProduct"),
          properties = null,
          spec = null,
          pluginXmlPath = null,
        )
      )
    )
    val model = baseModel.copy(
      discovery = discovery,
      projectRoot = tempDir,
      fileUpdater = fileUpdater,
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec)),
    )

    val ctx = ComputeContextImpl(model)
    runPlannerAndGenerator(ctx)

    val diffs = fileUpdater.getDiffs()
    assertThat(diffs).hasSize(1)
    val xml = diffs.single().expectedContent
    assertThat(xml).contains("<dependencies>")
    assertThat(xml).contains("<module name=\"intellij.owner.module\"/>")
    assertThat(xml).doesNotContain("<plugin id=\"intellij.owner.test.plugin\"/>")

    val errors = ctx.getNodeErrors(TestPluginXmlGenerator.id)
    assertThat(errors).isEmpty()
    }
  }

  @Test
  fun `sorts dependencies and content in DSL test plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("TestProduct") {
        bundlesPlugin("intellij.zeta.plugin")
        bundlesPlugin("intellij.alpha.plugin")
      }
      testPlugin("intellij.consumer.test.plugin") {
        pluginId("intellij.consumer.test.plugin")
        content("intellij.consumer.module")
      }
      plugin("intellij.zeta.plugin") { pluginId("intellij.zeta.plugin") }
      plugin("intellij.alpha.plugin") { pluginId("intellij.alpha.plugin") }
      plugin("intellij.owner.plugin") {
        content("intellij.libraries.zeta")
        content("intellij.libraries.alpha")
      }
      target("intellij.consumer.test.plugin") {
        dependsOn("intellij.zeta.plugin")
        dependsOn("intellij.alpha.plugin")
        dependsOn("intellij.libraries.zeta")
        dependsOn("intellij.libraries.alpha")
      }
      linkPluginMainTarget("intellij.consumer.test.plugin")
    }

    val nestedZetaB = moduleSet("nested.zeta.b") {
      module("intellij.zeta.nested.b2")
      module("intellij.zeta.nested.b1")
    }
    val nestedZetaA = moduleSet("nested.zeta.a") {
      module("intellij.zeta.nested.a2")
      module("intellij.zeta.nested.a1")
    }
    val zetaSet = moduleSet("zeta") {
      module("intellij.zeta.parent.z")
      module("intellij.zeta.parent.a")
      moduleSet(nestedZetaB)
      moduleSet(nestedZetaA)
    }
    val alphaSet = moduleSet("alpha") {
      module("intellij.alpha.parent.z")
      module("intellij.alpha.parent.a")
    }

    val spec = TestPluginSpec(
      pluginId = PluginId("intellij.consumer.test.plugin"),
      name = "Consumer Test Plugin",
      pluginXmlPath = "test-plugin/META-INF/plugin.xml",
      spec = productModules {
        moduleSet(zetaSet)
        moduleSet(alphaSet)
        requiredModule("intellij.extra.z")
        embeddedModule("intellij.extra.a")
      }
    )

    val fileUpdater = DeferredFileUpdater(tempDir)
    val baseModel = testGenerationModel(graph, fileUpdater = fileUpdater)
    val discovery = baseModel.discovery.copy(
      products = listOf(
        DiscoveredProduct(
          name = "TestProduct",
          config = ProductConfiguration(modules = emptyList(), className = "TestProduct"),
          properties = null,
          spec = null,
          pluginXmlPath = null,
        )
      )
    )
    val model = baseModel.copy(
      discovery = discovery,
      projectRoot = tempDir,
      fileUpdater = fileUpdater,
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec)),
    )

    val ctx = ComputeContextImpl(model)
    runPlannerAndGenerator(ctx)

    val diffs = fileUpdater.getDiffs()
    assertThat(diffs).hasSize(1)
    val xml = diffs.single().expectedContent

    fun indexOfToken(token: String): Int {
      val index = xml.indexOf(token)
      assertThat(index).describedAs("Missing '$token' in generated xml").isGreaterThanOrEqualTo(0)
      return index
    }

    val alphaPluginIndex = indexOfToken("<plugin id=\"intellij.alpha.plugin\"/>")
    val zetaPluginIndex = indexOfToken("<plugin id=\"intellij.zeta.plugin\"/>")
    val alphaModuleIndex = indexOfToken("<module name=\"intellij.libraries.alpha\"/>")
    val zetaModuleIndex = indexOfToken("<module name=\"intellij.libraries.zeta\"/>")

    assertThat(alphaPluginIndex).isLessThan(zetaPluginIndex)
    assertThat(alphaModuleIndex).isLessThan(zetaModuleIndex)
    assertThat(zetaPluginIndex).isLessThan(alphaModuleIndex)

    val alphaBlockIndex = indexOfToken("<!-- region alpha -->")
    val zetaBlockIndex = indexOfToken("<!-- region zeta -->")
    val nestedABlockIndex = indexOfToken("<!-- region nested.zeta.a -->")
    val nestedBBlockIndex = indexOfToken("<!-- region nested.zeta.b -->")
    val additionalBlockIndex = indexOfToken("<!-- region additional -->")

    assertThat(alphaBlockIndex).isLessThan(zetaBlockIndex)
    assertThat(zetaBlockIndex).isLessThan(nestedABlockIndex)
    assertThat(nestedABlockIndex).isLessThan(nestedBBlockIndex)
    assertThat(nestedBBlockIndex).isLessThan(additionalBlockIndex)

    assertThat(indexOfToken("<module name=\"intellij.zeta.parent.a\""))
      .isLessThan(indexOfToken("<module name=\"intellij.zeta.parent.z\""))
    assertThat(indexOfToken("<module name=\"intellij.alpha.parent.a\""))
      .isLessThan(indexOfToken("<module name=\"intellij.alpha.parent.z\""))
    assertThat(indexOfToken("<module name=\"intellij.zeta.nested.a1\""))
      .isLessThan(indexOfToken("<module name=\"intellij.zeta.nested.a2\""))
    assertThat(indexOfToken("<module name=\"intellij.zeta.nested.b1\""))
      .isLessThan(indexOfToken("<module name=\"intellij.zeta.nested.b2\""))
    assertThat(indexOfToken("<module name=\"intellij.extra.a\""))
      .isLessThan(indexOfToken("<module name=\"intellij.extra.z\""))
    }
  }

  private suspend fun runPlannerAndGenerator(
    ctx: ComputeContextImpl,
    contentModulePlans: List<ContentModuleDependencyPlan> = emptyList(),
  ) {
    ctx.initSlot(Slots.CONTENT_MODULE_PLAN)
    ctx.publish(Slots.CONTENT_MODULE_PLAN, ContentModuleDependencyPlanOutput(plans = contentModulePlans))
    ctx.initSlot(Slots.TEST_PLUGIN_DEPENDENCY_PLAN)
    val plannerCtx = ctx.forNode(TestPluginDependencyPlanner.id)
    TestPluginDependencyPlanner.execute(plannerCtx)
    ctx.finalizeNodeErrors(TestPluginDependencyPlanner.id)

    ctx.initSlot(Slots.TEST_PLUGINS)
    val nodeCtx = ctx.forNode(TestPluginXmlGenerator.id)
    TestPluginXmlGenerator.execute(nodeCtx)
    ctx.finalizeNodeErrors(TestPluginXmlGenerator.id)
  }
}
