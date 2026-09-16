// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.pipeline

import com.intellij.platform.buildScripts.concurrency.SharedTaskOwner
import com.intellij.platform.pluginGraph.TargetName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.createTestModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.dependency.jpsProject
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetGenerationConfig
import org.jetbrains.intellij.build.productLayout.discovery.PluginSource
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.productLayout.generator.PluginDependencyPlanner
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.productModules
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class ModelBuildingStageTest {
  private val owner = SharedTaskOwner("ModelBuildingStageTest")

  @org.junit.jupiter.api.AfterEach
  fun closeSharedTasks() {
    owner.close()
  }

  @Test
  fun `compatibility and packing seeds preserve descriptor ownership`(@TempDir tempDir: Path) {
    assertDescriptorOwnership(updateSuppressions = false, tempDir = tempDir)
  }

  @Test
  fun `updating suppressions preserves descriptor ownership`(@TempDir tempDir: Path) {
    assertDescriptorOwnership(updateSuppressions = true, tempDir = tempDir)
  }

  private fun assertDescriptorOwnership(updateSuppressions: Boolean, tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val pluginNames = listOf("bundled", "test", "compatible", "known", "packing", "managed", "legacy", "outer")
        .map { "intellij.scope.$it" }
      val jps = jpsProject(tempDir) {
        for (pluginName in pluginNames) {
          module(pluginName) {
            resourceRoot = "resources"
          }
        }
      }
      for (pluginName in pluginNames) {
        val dependencies = when (pluginName.substringAfterLast('.')) {
          "managed" -> """
            <dependencies>
              <!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->
              <!-- endregion -->
            </dependencies>
          """.trimIndent()
          "legacy" -> """
            <!-- editor-fold desc="Generated dependencies" -->
            <dependencies/>
            <!-- end editor-fold -->
          """.trimIndent()
          "outer" -> """
            <!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->
            <dependencies/>
            <!-- endregion -->
          """.trimIndent()
          else -> "<dependencies/>"
        }
        val descriptor = tempDir.resolve("${pluginName.replace('.', '/')}/resources/META-INF/plugin.xml")
        Files.createDirectories(descriptor.parent)
        Files.writeString(descriptor, "<idea-plugin><id>$pluginName</id>\n$dependencies\n</idea-plugin>")
      }
      val outputProvider = createTestModuleOutputProvider(jps.project)
      val products = listOf(DiscoveredProduct(
        name = "Demo",
        config = ProductConfiguration(modules = emptyList(), className = "DemoProperties"),
        properties = null,
        spec = productModules { bundledPlugins(listOf("intellij.scope.bundled")) },
        pluginXmlPath = null,
      ))
      val config = ModuleSetGenerationConfig(
        moduleSetSources = emptyMap(),
        discoveredProducts = products,
        projectRoot = tempDir,
        outputProvider = outputProvider,
        nonBundledPlugins = mapOf("Demo" to pluginNames.filter { it.substringAfterLast('.') !in setOf("known", "packing") }
          .mapTo(LinkedHashSet(), ::TargetName)),
        knownPlugins = setOf(TargetName("intellij.scope.known")),
        testPluginsByProduct = mapOf("Demo" to setOf(TargetName("intellij.scope.test"))),
        includeTestPluginDescriptorsFromSources = true,
        contentPluginPopulation = pluginNames.toSet(),
      )
      val model = ModelBuildingStage.execute(
        discovery = DiscoveryResult(
          moduleSetsByLabel = emptyMap(),
          products = products,
          testProductSpecs = emptyList(),
          moduleSetSources = emptyMap(),
        ),
        config = config,
        owner = owner,
        updateSuppressions = updateSuppressions,
        commitChanges = false,
        errorSink = ErrorSink(),
        phaseTimings = ArrayList(),
      )

      model.pluginGraph.query {
        for (pluginName in pluginNames) {
          assertThat(plugin(pluginName)).describedAs(pluginName).isNotNull()
        }
      }
      for (pluginName in pluginNames) {
        val expectedSource = when (pluginName.substringAfterLast('.')) {
          "bundled" -> PluginSource.BUNDLED
          "test" -> PluginSource.TEST
          else -> PluginSource.DISCOVERED
        }
        assertThat(model.pluginContentCache.getOrExtract(TargetName(pluginName))?.source)
          .describedAs(pluginName).isEqualTo(expectedSource)
      }

      val context = ComputeContextImpl(model)
      context.initSlot(Slots.PLUGIN_DEPENDENCY_PLAN)
      PluginDependencyPlanner.execute(context.forNode(PluginDependencyPlanner.id))
      val plans = context.get(Slots.PLUGIN_DEPENDENCY_PLAN).plans
      assertThat(plans.map { it.pluginContentModuleName.value }).containsExactlyInAnyOrder(
        "intellij.scope.bundled", "intellij.scope.test", "intellij.scope.managed", "intellij.scope.legacy", "intellij.scope.outer",
      )
    }
  }

  @Test
  fun `execute reads build-declared module set wrapper from disk`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.grid.core.plugin") {
          resourceRoot = "resources"
        }
      }
      val outputProvider = createTestModuleOutputProvider(jps.project)
      val wrapperModule = TargetName("intellij.grid.core.plugin")
      val pluginXmlPath = tempDir.resolve("intellij/grid/core/plugin/resources/META-INF/plugin.xml")
      Files.createDirectories(pluginXmlPath.parent)
      Files.writeString(
        pluginXmlPath,
        """
        <idea-plugin>
          <id>intellij.grid.core.plugin</id>
          <content namespace="jetbrains">
            <module name="intellij.grid"/>
          </content>
        </idea-plugin>
        """.trimIndent(),
      )
      val discovery = DiscoveryResult(
        moduleSetsByLabel = emptyMap(),
        products = listOf(
          DiscoveredProduct(
            name = "Idea",
            config = ProductConfiguration(modules = emptyList(), className = "IdeaProperties"),
            properties = null,
            spec = null,
            pluginXmlPath = null,
            bundledModuleSetPluginModules = listOf(wrapperModule),
          )
        ),
        testProductSpecs = emptyList(),
        moduleSetSources = emptyMap(),
      )
      val config = ModuleSetGenerationConfig(
        moduleSetSources = emptyMap(),
        discoveredProducts = emptyList(),
        projectRoot = tempDir,
        outputProvider = outputProvider,
      )

      val model = ModelBuildingStage.execute(
        discovery = discovery,
        config = config,
        updateSuppressions = false,
        commitChanges = false,
        errorSink = ErrorSink(),
        phaseTimings = ArrayList(), owner = owner,
      )

      val wrapperPlugin = model.pluginContentCache.getOrExtract(wrapperModule)
      assertThat(wrapperPlugin).isNotNull
      assertThat(wrapperPlugin!!.pluginXmlPath).isEqualTo(pluginXmlPath)
      assertThat(wrapperPlugin.pluginId).isNotNull
      assertThat(wrapperPlugin.pluginId!!.value).isEqualTo("intellij.grid.core.plugin")

      model.pluginGraph.query {
        val plugin = requireNotNull(plugin(wrapperModule.value))
        assertThat(plugin.isModuleSetWrapper).isTrue()
        val bundledPluginNames = mutableListOf<String>()
        requireNotNull(product("Idea")).bundles { bundledPluginNames.add(it.name().value) }
        assertThat(bundledPluginNames).contains(wrapperModule.value)

        val contentNames = mutableListOf<String>()
        plugin.containsContent { module, _ -> contentNames.add(module.name().value) }
        assertThat(contentNames).containsExactly("intellij.grid")
      }
    }
  }

  @Test
  fun `discoverPluginDescriptorsFromSources finds test plugin xml and the content population`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      module("intellij.test.plugin")
      module("intellij.content.plugin")
    }

    val testModuleDir = tempDir.resolve("intellij/test/plugin")
    val testResources = testModuleDir.resolve("testResources")
    Files.createDirectories(testResources.resolve("META-INF"))
    val testModule = jps.project.modules.first { it.name == "intellij.test.plugin" }
    testModule.addSourceRoot(JpsPathUtil.pathToUrl(testResources.toString()), JavaResourceRootType.TEST_RESOURCE)
    Files.writeString(testResources.resolve("META-INF/plugin.xml"), "<idea-plugin/>")

    val descriptors = ModelBuildingStage.discoverPluginDescriptorsFromSources(
      outputProvider = createTestModuleOutputProvider(jps.project),
      contentPluginPopulation = setOf("intellij.content.plugin", "intellij.plugin.this.project.does.not.hold"),
    )

    assertThat(descriptors.testPluginModules).containsExactly(TargetName("intellij.test.plugin"))
    assertThat(descriptors.pluginModules).containsExactly(TargetName("intellij.content.plugin"))
  }

  @Test
  fun `buildProductPluginXmlOverrides uses generated descriptor for discovered product module`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.product.plugin") {
          resourceRoot = "resources"
        }
        module("generated.module")
      }

      val stalePluginXmlPath = tempDir.resolve("intellij/product/plugin/resources/META-INF/plugin.xml")
      Files.createDirectories(stalePluginXmlPath.parent)
      Files.writeString(
        stalePluginXmlPath,
        """
        <idea-plugin>
          <content namespace="jetbrains">
            <module name="stale.module"/>
          </content>
        </idea-plugin>
        """.trimIndent(),
      )

      val relativePluginXmlPath = "intellij/product/plugin/resources/META-INF/plugin.xml"
      val overrides = ModelBuildingStage.buildProductPluginXmlOverrides(
        products = listOf(
          DiscoveredProduct(
            name = "Idea",
            config = ProductConfiguration(
              modules = emptyList(),
              className = "IdeaProperties",
              pluginXmlPath = relativePluginXmlPath,
            ),
            properties = null,
            spec = productModules {
              requiredModule("generated.module")
            },
            pluginXmlPath = relativePluginXmlPath,
          )
        ),
        outputProvider = createTestModuleOutputProvider(jps.project),
        projectRoot = tempDir,
        skipXIncludePaths = emptySet(),
        xIncludePrefixFilter = { null },
      )

      assertThat(overrides.keys).containsExactly(TargetName("intellij.product.plugin"))
      val generatedXml = overrides.getValue(TargetName("intellij.product.plugin")).pluginXmlContent
      assertThat(generatedXml).contains("generated.module")
      assertThat(generatedXml).doesNotContain("stale.module")
    }
  }

  @Test
  fun `buildProductPluginXmlOverrides skips valid source descriptor`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.product.plugin") {
          resourceRoot = "resources"
        }
        module("generated.module")
      }

      val sourcePluginXmlPath = tempDir.resolve("intellij/product/plugin/resources/META-INF/plugin.xml")
      Files.createDirectories(sourcePluginXmlPath.parent)
      Files.writeString(
        sourcePluginXmlPath,
        """
        <idea-plugin>
          <content namespace="jetbrains">
            <module name="generated.module"/>
          </content>
        </idea-plugin>
        """.trimIndent(),
      )

      val relativePluginXmlPath = "intellij/product/plugin/resources/META-INF/plugin.xml"
      val overrides = ModelBuildingStage.buildProductPluginXmlOverrides(
        products = listOf(
          DiscoveredProduct(
            name = "Idea",
            config = ProductConfiguration(
              modules = emptyList(),
              className = "IdeaProperties",
              pluginXmlPath = relativePluginXmlPath,
            ),
            properties = null,
            spec = productModules {
              requiredModule("generated.module")
            },
            pluginXmlPath = relativePluginXmlPath,
          )
        ),
        outputProvider = createTestModuleOutputProvider(jps.project),
        projectRoot = tempDir,
        skipXIncludePaths = emptySet(),
        xIncludePrefixFilter = { null },
      )

      assertThat(overrides).isEmpty()
    }
  }
}
