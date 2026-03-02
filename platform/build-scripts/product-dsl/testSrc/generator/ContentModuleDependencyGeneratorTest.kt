// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.dependency.generateDependencies
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.pluginTestSetup
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContextImpl
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Tests for [ContentModuleDependencyPlanner] and [ContentModuleXmlWriter] - content module dependency generation.
 */
@ExtendWith(TestFailureLogger::class)
class ContentModuleDependencyGeneratorTest {
  /**
   * Tests for test module dependency handling.
   *
   * Test modules (content modules ending with `._test`) are test descriptors declared in module sets.
   * They need their TEST scope JPS dependencies included in XML because they run in test context.
   *
   * Bug fix: Previously, generator used `withTests=false` for all modules, causing TEST scope deps
   * to be invisible and removed from test module XML files.
   */
  @Nested
  inner class TestModuleDependencyTest {
    @Test
    fun `test module includes TEST scope JPS dependencies in XML`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        // Setup: A test module (ending with ._test) with a TEST scope JPS dependency
        val setup = pluginTestSetup(tempDir) {
          // The dependency module (like a test library)
          contentModule("intellij.libraries.junit5.pioneer") {
            descriptor = """<idea-plugin package="org.junit.pioneer"/>"""
          }

          // The test module with TEST scope dependency
          contentModule("intellij.platform.testFramework._test") {
            descriptor = """
            <idea-plugin>
              <dependencies>
                <module name="intellij.libraries.junit5.pioneer"/>
              </dependencies>
            </idea-plugin>
          """.trimIndent()
            // TEST scope dependency - should be included for test modules
            jpsDependency("intellij.libraries.junit5.pioneer", JpsJavaDependencyScope.TEST)
          }

          // Plugin containing the test module
          plugin("intellij.platform.testFramework.plugin") {
            content("intellij.platform.testFramework._test")
          }
        }

        // Generate dependencies
        setup.generateDependencies(listOf("intellij.platform.testFramework.plugin"))

        // Verify: TEST scope dep is preserved in the test module's XML
        val diffs = setup.strategy.getDiffs()
        val testModuleDiff = diffs.find { it.path.toString().contains("intellij.platform.testFramework._test.xml") }

        // If there's no diff, the XML was unchanged (TEST dep was preserved)
        // If there IS a diff, verify it still contains the TEST scope dependency
        if (testModuleDiff != null) {
          assertThat(testModuleDiff.expectedContent)
            .describedAs("Test module XML should preserve TEST scope dependency")
            .contains("<module name=\"intellij.libraries.junit5.pioneer\"/>")
        }
        // If no diff, the original XML with the dependency was preserved - that's correct
      }
    }

    @Test
    fun `test module does not lose TEST scope dependency when regenerating`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        // Setup: A test module with existing TEST scope dependency in XML
        val setup = pluginTestSetup(tempDir) {
          // The dependency module
          contentModule("intellij.test.library") {
            descriptor = """<idea-plugin package="test.lib"/>"""
          }

          // Test module with TEST scope JPS dependency and existing XML dependency
          contentModule("intellij.feature._test") {
            descriptor = """
            <idea-plugin>
              <dependencies>
                <module name="intellij.test.library"/>
              </dependencies>
            </idea-plugin>
          """.trimIndent()
            jpsDependency("intellij.test.library", JpsJavaDependencyScope.TEST)
          }

          plugin("intellij.feature.plugin") {
            content("intellij.feature._test")
          }
        }

        setup.generateDependencies(listOf("intellij.feature.plugin"))

        // Verify: No diff means dependency was preserved, or diff still contains the dep
        val diffs = setup.strategy.getDiffs()
        val testModuleDiff = diffs.find { it.path.toString().contains("intellij.feature._test.xml") }

        if (testModuleDiff != null) {
          assertThat(testModuleDiff.expectedContent)
            .describedAs("Test module should not lose TEST scope dependency")
            .contains("<module name=\"intellij.test.library\"/>")
        }
      }
    }

    @Test
    fun `regular module does not include TEST scope dependencies in XML`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        // Setup: A regular (non-test) module with TEST scope JPS dependency
        val setup = pluginTestSetup(tempDir) {
          // The dependency module (test-only library)
          contentModule("intellij.test.only.lib") {
            descriptor = """<idea-plugin package="test.only"/>"""
          }

          // Regular module (NOT ending with ._test) with TEST scope dependency
          contentModule("intellij.regular.module") {
            descriptor = """<idea-plugin package="regular"/>"""
            // TEST scope - should NOT be included for regular modules
            jpsDependency("intellij.test.only.lib", JpsJavaDependencyScope.TEST)
          }

          plugin("intellij.regular.plugin") {
            content("intellij.regular.module")
          }
        }

        setup.generateDependencies(listOf("intellij.regular.plugin"))

        // Verify: Regular module should NOT have the TEST scope dependency in XML
        val diffs = setup.strategy.getDiffs()
        val regularModuleDiff = diffs.find { it.path.toString().contains("intellij.regular.module.xml") }

        // Either no diff (no deps added), or diff doesn't contain the TEST dep
        if (regularModuleDiff != null) {
          assertThat(regularModuleDiff.expectedContent)
            .describedAs("Regular module should not include TEST scope dependency in XML")
            .doesNotContain("<module name=\"intellij.test.only.lib\"/>")
        }
      }
    }

    @Test
    fun `test module includes both COMPILE and TEST scope dependencies`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        // Setup: Test module with both COMPILE and TEST scope dependencies
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.compile.dep") {
            descriptor = """<idea-plugin package="compile.dep"/>"""
          }
          contentModule("intellij.test.dep") {
            descriptor = """<idea-plugin package="test.dep"/>"""
          }

          contentModule("intellij.mixed._test") {
            descriptor = """
            <idea-plugin>
              <dependencies>
                <module name="intellij.compile.dep"/>
                <module name="intellij.test.dep"/>
              </dependencies>
            </idea-plugin>
          """.trimIndent()
            jpsDependency("intellij.compile.dep", JpsJavaDependencyScope.COMPILE)
            jpsDependency("intellij.test.dep", JpsJavaDependencyScope.TEST)
          }

          plugin("intellij.mixed.plugin") {
            content("intellij.mixed._test")
          }
        }

        setup.generateDependencies(listOf("intellij.mixed.plugin"))

        // Verify: Test module should have BOTH dependencies
        val diffs = setup.strategy.getDiffs()
        val testModuleDiff = diffs.find { it.path.toString().contains("intellij.mixed._test.xml") }

        if (testModuleDiff != null) {
          assertThat(testModuleDiff.expectedContent)
            .describedAs("Test module should include COMPILE scope dependency")
            .contains("<module name=\"intellij.compile.dep\"/>")
          assertThat(testModuleDiff.expectedContent)
            .describedAs("Test module should include TEST scope dependency")
            .contains("<module name=\"intellij.test.dep\"/>")
        }
      }
    }
  }

  @Nested
  inner class TestPluginContentModuleTest {
    @Test
    fun `test plugin content modules are processed`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.libraries.junit5.vintage") {
            descriptor = """<idea-plugin package="org.junit.vintage"/>"""
          }
          contentModule("intellij.tools.testsBootstrap") {
            descriptor = """<idea-plugin package="com.intellij.tests.bootstrap"/>"""
            jpsDependency("intellij.libraries.junit5.vintage", JpsJavaDependencyScope.COMPILE)
          }
          plugin("intellij.test.plugin") {
            isTestPlugin = true
            content("intellij.tools.testsBootstrap")
            content("intellij.libraries.junit5.vintage")
          }
        }

        val model = testGenerationModel(
          pluginGraph = setup.pluginGraph,
          outputProvider = setup.jps.outputProvider,
          fileUpdater = setup.strategy,
        )

        val ctx = ComputeContextImpl(model)
        ctx.initSlot(Slots.CONTENT_MODULE_PLAN)
        ctx.initSlot(Slots.CONTENT_MODULE)
        val planCtx = ctx.forNode(ContentModuleDependencyPlanner.id)
        ContentModuleDependencyPlanner.execute(planCtx)
        ctx.finalizeNodeErrors(ContentModuleDependencyPlanner.id)

        val writeCtx = ctx.forNode(ContentModuleXmlWriter.id)
        ContentModuleXmlWriter.execute(writeCtx)
        ctx.finalizeNodeErrors(ContentModuleXmlWriter.id)

        val diffs = setup.strategy.getDiffs()
        val testBootstrapDiff = diffs.find { it.path.toString().contains("intellij.tools.testsBootstrap.xml") }
        assertThat(testBootstrapDiff)
          .describedAs("Test plugin content module should be processed by dependency planner")
          .isNotNull()
        assertThat(testBootstrapDiff!!.expectedContent)
          .contains("<module name=\"intellij.libraries.junit5.vintage\"/>")
      }
    }

    @Test
    fun `test plugin only module includes TEST scope dependencies in written deps`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.test.only.lib") {
            descriptor = """<idea-plugin package="test.only.lib"/>"""
          }

          contentModule("intellij.test.only.consumer") {
            descriptor = """<idea-plugin package="test.only.consumer"/>"""
            jpsDependency("intellij.test.only.lib", JpsJavaDependencyScope.TEST)
          }

          plugin("intellij.test.plugin") {
            isTestPlugin = true
            content("intellij.test.only.consumer")
            content("intellij.test.only.lib")
          }
        }

        val graph = pluginGraph {
          moduleWithScopedDeps("intellij.test.only.lib")
          moduleWithScopedDeps("intellij.test.only.consumer", "intellij.test.only.lib" to "TEST")
          testPlugin("intellij.test.plugin") {
            testContent("intellij.test.only.consumer")
            testContent("intellij.test.only.lib")
          }
        }

        val model = testGenerationModel(
          pluginGraph = graph,
          outputProvider = setup.jps.outputProvider,
          fileUpdater = setup.strategy,
        )

        val ctx = ComputeContextImpl(model)
        ctx.initSlot(Slots.CONTENT_MODULE_PLAN)
        ctx.initSlot(Slots.CONTENT_MODULE)
        val planCtx = ctx.forNode(ContentModuleDependencyPlanner.id)
        ContentModuleDependencyPlanner.execute(planCtx)
        ctx.finalizeNodeErrors(ContentModuleDependencyPlanner.id)

        val writeCtx = ctx.forNode(ContentModuleXmlWriter.id)
        ContentModuleXmlWriter.execute(writeCtx)
        ctx.finalizeNodeErrors(ContentModuleXmlWriter.id)

        val diffs = setup.strategy.getDiffs()
        val consumerDiff = diffs.find { it.path.toString().contains("intellij.test.only.consumer.xml") }
        assertThat(consumerDiff)
          .describedAs("Module used only from test plugin should include TEST scope dependency")
          .isNotNull()
        assertThat(consumerDiff!!.expectedContent)
          .contains("<module name=\"intellij.test.only.lib\"/>")
      }
    }

    @Test
    fun `test plugin only module bypasses library filter for written and test deps`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.libraries.assertj.core") {
            descriptor = """<idea-plugin package="assertj"/>"""
          }

          contentModule("intellij.test.only.consumer") {
            descriptor = """<idea-plugin package="test.only.consumer"/>"""
            jpsDependency("intellij.libraries.assertj.core", JpsJavaDependencyScope.TEST)
          }

          plugin("intellij.test.plugin") {
            isTestPlugin = true
            content("intellij.test.only.consumer")
            content("intellij.libraries.assertj.core")
          }
        }

        val graph = pluginGraph {
          moduleWithScopedDeps("intellij.libraries.assertj.core")
          moduleWithScopedDeps("intellij.test.only.consumer", "intellij.libraries.assertj.core" to "TEST")
          testPlugin("intellij.test.plugin") {
            testContent("intellij.test.only.consumer")
            testContent("intellij.libraries.assertj.core")
          }
        }

        val descriptorCache = ModuleDescriptorCache(setup.jps.outputProvider, this)
        val generation = planContentModuleDependenciesWithBothSets(
          contentModuleName = ContentModuleName("intellij.test.only.consumer"),
          descriptorCache = descriptorCache,
          pluginGraph = graph,
          isTestDescriptor = false,
          suppressionConfig = SuppressionConfig(),
          updateSuppressions = false,
          libraryModuleFilter = { libraryModuleName -> !libraryModuleName.contains(".assertj.") },
        )
        val plan = generation.plan
        assertThat(plan).isNotNull()

        assertThat(plan!!.moduleDependencies)
          .describedAs("Test-only module should keep filtered library in written deps")
          .contains(ContentModuleName("intellij.libraries.assertj.core"))
        assertThat(plan.testDependencies)
          .describedAs("Test-only module should keep filtered library in test deps")
          .contains(ContentModuleName("intellij.libraries.assertj.core"))
      }
    }

    @Test
    fun `module shared with production source does not include TEST scope dependency in written deps`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.test.only.lib") {
            descriptor = """<idea-plugin package="test.only.lib"/>"""
          }

          contentModule("intellij.shared.consumer") {
            descriptor = """<idea-plugin package="shared.consumer"/>"""
            jpsDependency("intellij.test.only.lib", JpsJavaDependencyScope.TEST)
          }

          plugin("intellij.production.plugin") {
            content("intellij.shared.consumer")
          }

          plugin("intellij.test.plugin") {
            isTestPlugin = true
            content("intellij.shared.consumer")
            content("intellij.test.only.lib")
          }
        }

        val result = setup.generateDependencies(listOf("intellij.production.plugin", "intellij.test.plugin"))
        val contentResults = result.files
          .flatMap { it.contentModuleResults }
          .filter { it.contentModuleName == ContentModuleName("intellij.shared.consumer") }

        assertThat(contentResults)
          .describedAs("Shared module should be present in generation results")
          .isNotEmpty()
        for (contentResult in contentResults) {
          assertThat(contentResult.writtenDependencies)
            .describedAs("Module with a production content source should keep TEST scope deps out of written XML")
            .doesNotContain(ContentModuleName("intellij.test.only.lib"))
        }
      }
    }

    @Test
    fun `module shared with production source still applies library filter`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.libraries.assertj.core") {
            descriptor = """<idea-plugin package="assertj"/>"""
          }

          contentModule("intellij.shared.consumer") {
            descriptor = """<idea-plugin package="shared.consumer"/>"""
            jpsDependency("intellij.libraries.assertj.core", JpsJavaDependencyScope.TEST)
          }

          plugin("intellij.production.plugin") {
            content("intellij.shared.consumer")
          }

          plugin("intellij.test.plugin") {
            isTestPlugin = true
            content("intellij.shared.consumer")
            content("intellij.libraries.assertj.core")
          }
        }

        val graph = pluginGraph {
          moduleWithScopedDeps("intellij.libraries.assertj.core")
          moduleWithScopedDeps("intellij.shared.consumer", "intellij.libraries.assertj.core" to "TEST")
          plugin("intellij.production.plugin") {
            content("intellij.shared.consumer")
          }
          testPlugin("intellij.test.plugin") {
            testContent("intellij.shared.consumer")
            testContent("intellij.libraries.assertj.core")
          }
        }

        val descriptorCache = ModuleDescriptorCache(setup.jps.outputProvider, this)
        val generation = planContentModuleDependenciesWithBothSets(
          contentModuleName = ContentModuleName("intellij.shared.consumer"),
          descriptorCache = descriptorCache,
          pluginGraph = graph,
          isTestDescriptor = false,
          suppressionConfig = SuppressionConfig(),
          updateSuppressions = false,
          libraryModuleFilter = { libraryModuleName -> !libraryModuleName.contains(".assertj.") },
        )
        val plan = generation.plan
        assertThat(plan).isNotNull()

        assertThat(plan!!.moduleDependencies)
          .describedAs("Shared module should keep production library filtering in written deps")
          .doesNotContain(ContentModuleName("intellij.libraries.assertj.core"))
        assertThat(plan.testDependencies)
          .describedAs("Shared module should keep production library filtering in test deps")
          .doesNotContain(ContentModuleName("intellij.libraries.assertj.core"))
      }
    }
  }

  /**
   * Tests for globally embedded module filtering.
   *
   * Globally embedded modules (in EMBEDDED sources across all relevant products,
   * including bundled plugin content) are skipped when generating dependencies for content modules
   * that are in plugins.
   * Content modules directly in products DO include embedded module dependencies.
   */
  @Nested
  inner class EmbeddedModuleFilteringTest {
    @Test
    fun `content module in plugin skips globally embedded dependency`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        // Setup: Content module in a plugin depends on a globally embedded module
        val setup = pluginTestSetup(tempDir) {
          // The embedded module - in EMBEDDED module set, no plugin source
          contentModule("intellij.platform.core") {
            descriptor = """<idea-plugin package="com.intellij.core"/>"""
          }

          // Content module with JPS dependency on embedded module
          contentModule("intellij.my.feature") {
            descriptor = """<idea-plugin package="com.intellij.feature"/>"""
            jpsDependency("intellij.platform.core")
          }

          // Plugin containing the content module
          plugin("intellij.my.plugin") {
            content("intellij.my.feature")
          }

          // Product with EMBEDDED module set containing the dependency
          product("TestProduct") {
            bundlesPlugin("intellij.my.plugin")
            moduleSet("essential") {
              module("intellij.platform.core", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
            }
          }
        }

        setup.generateDependencies(listOf("intellij.my.plugin"))

        // Verify: Content module XML should NOT have the embedded dependency
        val diffs = setup.strategy.getDiffs()
        val contentModuleDiff = diffs.find { it.path.toString().contains("intellij.my.feature.xml") }

        if (contentModuleDiff != null) {
          assertThat(contentModuleDiff.expectedContent)
            .describedAs("Content module in plugin should skip globally embedded dependency")
            .doesNotContain("<module name=\"intellij.platform.core\"/>")
        }
      }
    }

    @Test
    fun `content module in plugin excludes embedded deps from written list`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.platform.core") {
            descriptor = """<idea-plugin package="com.intellij.core"/>"""
          }

          contentModule("intellij.my.feature") {
            descriptor = """<idea-plugin package="com.intellij.feature"/>"""
            jpsDependency("intellij.platform.core")
          }

          plugin("intellij.my.plugin") {
            content("intellij.my.feature")
          }

          product("TestProduct") {
            bundlesPlugin("intellij.my.plugin")
            moduleSet("essential") {
              module("intellij.platform.core", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
            }
          }
        }

        val result = setup.generateDependencies(listOf("intellij.my.plugin"))
        val contentResult = result.files
          .flatMap { it.contentModuleResults }
          .single { it.contentModuleName == ContentModuleName("intellij.my.feature") }

        assertThat(contentResult.writtenDependencies)
          .describedAs("Content module in plugin should not write globally embedded deps")
          .doesNotContain(ContentModuleName("intellij.platform.core"))
      }
    }

    @Test
    fun `module with plugin source is still treated as globally embedded`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.platform.core") {
            descriptor = """<idea-plugin package="com.intellij.core"/>"""
          }

          contentModule("intellij.my.feature") {
            descriptor = """<idea-plugin package="com.intellij.feature"/>"""
            jpsDependency("intellij.platform.core")
          }

          plugin("intellij.core.plugin") {
            content("intellij.platform.core", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
          }

          plugin("intellij.my.plugin") {
            content("intellij.my.feature")
          }

          product("TestProduct") {
            bundlesPlugin("intellij.my.plugin")
            bundlesPlugin("intellij.core.plugin")
            moduleSet("essential") {
              module("intellij.platform.core", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
            }
          }
        }

        val result = setup.generateDependencies(listOf("intellij.my.plugin", "intellij.core.plugin"))
        val contentResult = result.files
          .flatMap { it.contentModuleResults }
          .single { it.contentModuleName == ContentModuleName("intellij.my.feature") }

        assertThat(contentResult.writtenDependencies)
          .describedAs("Target remains globally embedded when reachable from product/module sets")
          .doesNotContain(ContentModuleName("intellij.platform.core"))
      }
    }

    @Test
    fun `content module in module set does not skip embedded deps`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.platform.core") {
            descriptor = """<idea-plugin package="com.intellij.core"/>"""
          }

          contentModule("intellij.shared.module") {
            descriptor = """<idea-plugin package="com.intellij.shared"/>"""
            jpsDependency("intellij.platform.core")
          }

          plugin("intellij.shared.plugin") {
            content("intellij.shared.module")
          }

          product("TestProduct") {
            bundlesPlugin("intellij.shared.plugin")
            moduleSet("essential") {
              module("intellij.platform.core", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
            }
            moduleSet("shared.set") {
              module("intellij.shared.module")
            }
          }
        }

        val result = setup.generateDependencies(listOf("intellij.shared.plugin"))
        val contentResult = result.files
          .flatMap { it.contentModuleResults }
          .single { it.contentModuleName == ContentModuleName("intellij.shared.module") }

        assertThat(contentResult.writtenDependencies)
          .describedAs("Module set content should keep embedded deps")
          .contains(ContentModuleName("intellij.platform.core"))
      }
    }

    @Test
    fun `content module in plugin keeps dependency embedded only in subset of products`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.platform.frontend.split") {
            descriptor = """<idea-plugin package="com.intellij.frontend.split"/>"""
          }

          contentModule("intellij.my.feature") {
            descriptor = """<idea-plugin package="com.intellij.feature"/>"""
            jpsDependency("intellij.platform.frontend.split")
          }

          plugin("intellij.my.plugin") {
            content("intellij.my.feature")
          }

          product("Idea") {
            bundlesPlugin("intellij.my.plugin")
          }

          product("JetBrainsClient") {
            bundlesPlugin("intellij.my.plugin")
            moduleSet("client.set") {
              module("intellij.platform.frontend.split", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
            }
          }
        }

        val result = setup.generateDependencies(listOf("intellij.my.plugin"))
        val contentResult = result.files
          .flatMap { it.contentModuleResults }
          .single { it.contentModuleName == ContentModuleName("intellij.my.feature") }

        assertThat(contentResult.writtenDependencies)
          .describedAs("Dependency must be kept when target is not globally embedded")
          .contains(ContentModuleName("intellij.platform.frontend.split"))
      }
    }

    @Test
    fun `content module in plugin skips dependency embedded in all bundled products`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.platform.frontend.split") {
            descriptor = """<idea-plugin package="com.intellij.frontend.split"/>"""
          }

          contentModule("intellij.my.feature") {
            descriptor = """<idea-plugin package="com.intellij.feature"/>"""
            jpsDependency("intellij.platform.frontend.split")
          }

          plugin("intellij.my.plugin") {
            content("intellij.my.feature")
          }

          product("Idea") {
            // Plugin is not bundled in Idea.
          }

          product("JetBrainsClient") {
            bundlesPlugin("intellij.my.plugin")
            moduleSet("client.set") {
              module("intellij.platform.frontend.split", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
            }
          }
        }

        val result = setup.generateDependencies(listOf("intellij.my.plugin"))
        val contentResult = result.files
          .flatMap { it.contentModuleResults }
          .single { it.contentModuleName == ContentModuleName("intellij.my.feature") }

        assertThat(contentResult.writtenDependencies)
          .describedAs("Dependency should be skipped when embedded in all products where owner plugin is bundled")
          .doesNotContain(ContentModuleName("intellij.platform.frontend.split"))
      }
    }

    @Test
    fun `content module in plugin skips dependency when only CodeServer misses bundled owner`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.platform.ide.impl") {
            descriptor = """<idea-plugin package="com.intellij.ide.impl"/>"""
          }

          contentModule("intellij.my.feature") {
            descriptor = """<idea-plugin package="com.intellij.feature"/>"""
            jpsDependency("intellij.platform.ide.impl")
          }

          plugin("intellij.platform.owner") {
            content("intellij.platform.ide.impl", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
          }

          plugin("intellij.my.plugin") {
            content("intellij.my.feature")
          }

          product("CodeServer") {
            bundlesPlugin("intellij.my.plugin")
          }

          product("Idea") {
            bundlesPlugin("intellij.my.plugin")
            bundlesPlugin("intellij.platform.owner")
          }
        }

        val result = setup.generateDependencies(listOf("intellij.my.plugin", "intellij.platform.owner"))
        val contentResult = result.files
          .flatMap { it.contentModuleResults }
          .single { it.contentModuleName == ContentModuleName("intellij.my.feature") }

        assertThat(contentResult.writtenDependencies)
          .describedAs("CodeServer must be excluded from embedded-check scope")
          .doesNotContain(ContentModuleName("intellij.platform.ide.impl"))
      }
    }

    @Test
    fun `content module in non-bundled plugin skips globally embedded dependency`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("intellij.platform.core") {
            descriptor = """<idea-plugin package="com.intellij.core"/>"""
          }

          contentModule("intellij.my.feature") {
            descriptor = """<idea-plugin package="com.intellij.feature"/>"""
            jpsDependency("intellij.platform.core")
          }

          plugin("intellij.my.plugin") {
            content("intellij.my.feature")
          }

          // Plugin intentionally remains non-bundled.
          product("TestProduct") {
            moduleSet("essential") {
              module("intellij.platform.core", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
            }
          }
        }

        val result = setup.generateDependencies(listOf("intellij.my.plugin"))
        val contentResult = result.files
          .flatMap { it.contentModuleResults }
          .single { it.contentModuleName == ContentModuleName("intellij.my.feature") }

        assertThat(contentResult.writtenDependencies)
          .describedAs("Non-bundled plugin content should skip globally embedded dependency")
          .doesNotContain(ContentModuleName("intellij.platform.core"))
      }
    }

    @Test
    fun `content module with dependency in plugin does not skip it`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        // Setup: Dependency module is in a plugin (not globally embedded)
        val setup = pluginTestSetup(tempDir) {
          // Dependency module - in a plugin, so NOT globally embedded
          contentModule("intellij.vcs.core") {
            descriptor = """<idea-plugin package="com.intellij.vcs"/>"""
          }

          // Plugin containing the dependency module
          plugin("intellij.vcs.plugin") {
            content("intellij.vcs.core")
          }

          // Content module depending on the plugin's content
          contentModule("intellij.my.feature") {
            descriptor = """<idea-plugin package="com.intellij.feature"/>"""
            jpsDependency("intellij.vcs.core")
          }

          // Plugin containing our content module
          plugin("intellij.my.plugin") {
            content("intellij.my.feature")
          }

          product("TestProduct") {
            bundlesPlugin("intellij.my.plugin")
            bundlesPlugin("intellij.vcs.plugin")
          }
        }

        setup.generateDependencies(listOf("intellij.my.plugin", "intellij.vcs.plugin"))

        // Verify: Content module should have the dependency (it's in a plugin, not embedded)
        val diffs = setup.strategy.getDiffs()
        val contentModuleDiff = diffs.find { it.path.toString().contains("intellij.my.feature.xml") }

        // Either no change (dep already exists) or change includes the dep
        if (contentModuleDiff != null) {
          assertThat(contentModuleDiff.expectedContent)
            .describedAs("Dependency in plugin is NOT globally embedded, should be included")
            .contains("<module name=\"intellij.vcs.core\"/>")
        }
      }
    }

    @Test
    fun `module with non-EMBEDDED loading is not skipped`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        // Setup: Module in module set but with REQUIRED loading (not EMBEDDED)
        val setup = pluginTestSetup(tempDir) {
          // Module with REQUIRED loading (not globally embedded)
          contentModule("intellij.platform.optional") {
            descriptor = """<idea-plugin package="com.intellij.optional"/>"""
          }

          contentModule("intellij.my.feature") {
            descriptor = """<idea-plugin package="com.intellij.feature"/>"""
            jpsDependency("intellij.platform.optional")
          }

          plugin("intellij.my.plugin") {
            content("intellij.my.feature")
          }

          product("TestProduct") {
            bundlesPlugin("intellij.my.plugin")
            moduleSet("optional.set") {
              // REQUIRED loading, not EMBEDDED
              module("intellij.platform.optional", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.REQUIRED)
            }
          }
        }

        setup.generateDependencies(listOf("intellij.my.plugin"))

        // Verify: Dependency with REQUIRED loading should be included
        val diffs = setup.strategy.getDiffs()
        val contentModuleDiff = diffs.find { it.path.toString().contains("intellij.my.feature.xml") }

        if (contentModuleDiff != null) {
          assertThat(contentModuleDiff.expectedContent)
            .describedAs("Module with REQUIRED loading is NOT globally embedded, should be included")
            .contains("<module name=\"intellij.platform.optional\"/>")
        }
      }
    }
  }

  @Nested
  inner class SelfDependencyTest {
    @Test
    fun `content module written deps exclude self dependency`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val moduleName = "intellij.self.module"
        val pluginName = "intellij.self.plugin"
        val setup = pluginTestSetup(tempDir) {
          contentModule(moduleName) {
            descriptor = """<idea-plugin package="self.module"/>"""
            jpsDependency(moduleName, JpsJavaDependencyScope.COMPILE)
          }
          plugin(pluginName) {
            content(moduleName)
          }
        }

        val result = setup.generateDependencies(listOf(pluginName))
        val contentResult = result.files
          .flatMap { it.contentModuleResults }
          .single { it.contentModuleName == ContentModuleName(moduleName) }

        assertThat(contentResult.writtenDependencies)
          .describedAs("Self dependencies should not be written for content modules")
          .doesNotContain(ContentModuleName(moduleName))
      }
    }
  }

  @Nested
  inner class UpdateSuppressionsDslTestPluginPolicyTest {
    @Test
    fun `pure dsl test owned module respects explicit suppressions config in regular generation`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("owner.content") {
            descriptor = """<idea-plugin package="owner.content"/>"""
            jpsDependency("dep.plugin")
          }
          contentModule("dep.plugin") {
            descriptor = """<idea-plugin package="dep.plugin"/>"""
          }
          plugin("test.plugin") {
            isTestPlugin = true
            content("owner.content")
          }
          plugin("dep.plugin")
        }

        val graph = pluginGraph {
          moduleWithScopedDeps("owner.content", "dep.plugin" to "COMPILE")
          testPlugin("test.plugin") {
            pluginId("test.plugin")
            content("owner.content")
          }
          plugin("dep.plugin") {
            pluginId("dep.plugin")
          }
          linkPluginMainTarget("dep.plugin")
        }

        val suppressionConfig = SuppressionConfig(
          contentModules = mapOf(
            ContentModuleName("owner.content") to org.jetbrains.intellij.build.productLayout.config.ContentModuleSuppression(
              suppressPlugins = setOf(PluginId("dep.plugin"))
            )
          )
        )

        val descriptorCache = ModuleDescriptorCache(setup.jps.outputProvider, this)
        val generation = planContentModuleDependenciesWithBothSets(
          contentModuleName = ContentModuleName("owner.content"),
          descriptorCache = descriptorCache,
          pluginGraph = graph,
          isTestDescriptor = false,
          suppressionConfig = suppressionConfig,
          updateSuppressions = false,
          libraryModuleFilter = { true },
        )
        val plan = generation.plan
        assertThat(plan).isNotNull()

        assertThat(plan!!.suppressedPlugins)
          .describedAs("Suppressions config should be honored when computing written plugin deps")
          .contains(PluginId("dep.plugin"))
        assertThat(plan.writtenPluginDependencies)
          .doesNotContain(PluginId("dep.plugin"))
      }
    }

    @Test
    fun `dsl declared module with mixed ownership respects suppressions config in regular generation`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("shared.content") {
            descriptor = """<idea-plugin package="shared.content"/>"""
            jpsDependency("dep.plugin")
          }
          contentModule("dep.plugin") {
            descriptor = """<idea-plugin package="dep.plugin"/>"""
          }
          plugin("test.plugin") {
            isTestPlugin = true
            content("shared.content")
          }
          plugin("owner.plugin") {
            content("shared.content")
          }
          plugin("dep.plugin")
        }

        val graph = pluginGraph {
          moduleWithScopedDeps("shared.content", "dep.plugin" to "COMPILE")
          testPlugin("test.plugin") {
            pluginId("test.plugin")
            content("shared.content")
          }
          plugin("owner.plugin") {
            pluginId("owner.plugin")
            content("shared.content")
          }
          plugin("dep.plugin") {
            pluginId("dep.plugin")
          }
          linkPluginMainTarget("dep.plugin")
        }

        val suppressionConfig = SuppressionConfig(
          contentModules = mapOf(
            ContentModuleName("shared.content") to org.jetbrains.intellij.build.productLayout.config.ContentModuleSuppression(
              suppressPlugins = setOf(PluginId("dep.plugin"))
            )
          )
        )

        val descriptorCache = ModuleDescriptorCache(setup.jps.outputProvider, this)
        val generation = planContentModuleDependenciesWithBothSets(
          contentModuleName = ContentModuleName("shared.content"),
          descriptorCache = descriptorCache,
          pluginGraph = graph,
          isTestDescriptor = false,
          suppressionConfig = suppressionConfig,
          updateSuppressions = false,
          libraryModuleFilter = { true },
        )
        val plan = generation.plan
        assertThat(plan).isNotNull()

        assertThat(plan!!.suppressedPlugins)
          .describedAs("Suppressions config should remain effective for mixed-ownership modules")
          .contains(PluginId("dep.plugin"))
        assertThat(plan.writtenPluginDependencies)
          .doesNotContain(PluginId("dep.plugin"))
      }
    }

    @Test
    fun `pure dsl test owned module auto-suppresses missing plugin deps in updateSuppressions`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("owner.content") {
            descriptor = """<idea-plugin package="owner.content"/>"""
            jpsDependency("dep.plugin")
          }
          contentModule("dep.plugin") {
            descriptor = """<idea-plugin package="dep.plugin"/>"""
          }
          plugin("test.plugin") {
            isTestPlugin = true
            content("owner.content")
          }
          plugin("dep.plugin")
        }

        val graph = pluginGraph {
          moduleWithScopedDeps("owner.content", "dep.plugin" to "COMPILE")
          testPlugin("test.plugin") {
            pluginId("test.plugin")
            content("owner.content")
          }
          plugin("dep.plugin") {
            pluginId("dep.plugin")
          }
          linkPluginMainTarget("dep.plugin")
        }

        val descriptorCache = ModuleDescriptorCache(setup.jps.outputProvider, this)
        val generation = planContentModuleDependenciesWithBothSets(
          contentModuleName = ContentModuleName("owner.content"),
          descriptorCache = descriptorCache,
          pluginGraph = graph,
          isTestDescriptor = false,
          suppressionConfig = SuppressionConfig(),
          updateSuppressions = true,
          libraryModuleFilter = { true },
        )
        val plan = generation.plan
        assertThat(plan).isNotNull()

        assertThat(plan!!.allJpsPluginDependencies)
          .containsExactly(PluginId("dep.plugin"))
        assertThat(plan.suppressedPlugins)
          .describedAs("updateSuppressions should auto-capture missing plugin deps into suppressions")
          .contains(PluginId("dep.plugin"))
        assertThat(plan.suppressionUsages)
          .anyMatch {
            it.type == SuppressionType.PLUGIN_DEP &&
            it.sourceModule == ContentModuleName("owner.content") &&
            it.suppressedDep == "dep.plugin"
          }
      }
    }

    @Test
    fun `regular module still auto-suppresses missing plugin deps in updateSuppressions`(@TempDir tempDir: Path) {
      runBlocking(Dispatchers.Default) {
        val setup = pluginTestSetup(tempDir) {
          contentModule("owner.content") {
            descriptor = """<idea-plugin package="owner.content"/>"""
            jpsDependency("dep.plugin")
          }
          contentModule("dep.plugin") {
            descriptor = """<idea-plugin package="dep.plugin"/>"""
          }
          plugin("owner.plugin") {
            content("owner.content")
          }
          plugin("dep.plugin")
        }

        val graph = pluginGraph {
          moduleWithScopedDeps("owner.content", "dep.plugin" to "COMPILE")
          plugin("owner.plugin") {
            pluginId("owner.plugin")
            content("owner.content")
          }
          plugin("dep.plugin") {
            pluginId("dep.plugin")
          }
          linkPluginMainTarget("dep.plugin")
        }

        val descriptorCache = ModuleDescriptorCache(setup.jps.outputProvider, this)
        val generation = planContentModuleDependenciesWithBothSets(
          contentModuleName = ContentModuleName("owner.content"),
          descriptorCache = descriptorCache,
          pluginGraph = graph,
          isTestDescriptor = false,
          suppressionConfig = SuppressionConfig(),
          updateSuppressions = true,
          libraryModuleFilter = { true },
        )
        val plan = generation.plan
        assertThat(plan).isNotNull()

        assertThat(plan!!.suppressedPlugins)
          .contains(PluginId("dep.plugin"))
        assertThat(plan.suppressionUsages)
          .anyMatch {
            it.type == SuppressionType.PLUGIN_DEP &&
            it.sourceModule == ContentModuleName("owner.content") &&
            it.suppressedDep == "dep.plugin"
          }
      }
    }
  }
}
