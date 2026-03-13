// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.dependency

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.config.ContentModuleSuppression
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginDependencyError
import org.jetbrains.intellij.build.productLayout.model.error.ErrorCategory
import org.jetbrains.intellij.build.productLayout.model.error.PluginDependencyError
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.validator.rule.createResolutionQuery
import org.jetbrains.intellij.build.productLayout.validator.rule.existsAnywhere
import org.jetbrains.intellij.build.productLayout.validator.rule.forProductionPlugin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Tests for PluginDependencyGenerator.kt functions.
 *
 * Note: isTestPlugin detection is tested directly in PluginDependencyResolutionTest.IsTestPluginTest.
 */
@ExtendWith(TestFailureLogger::class)
class PluginDependencyGeneratorTest {
  // --- Graph-based resolution and validation tests ---

  @Test
  fun `findUnresolvedDeps identifies deps with no resolution sources`() {
    val graph = pluginGraph {
      product("TestProduct") {
        bundlesPlugin("intellij.platform.vcs")
        includesModuleSet("ide.common")
      }
      plugin("intellij.platform.vcs") { content("intellij.platform.vcs.impl") }
      moduleSet("ide.common") { module("intellij.platform.ide.core") }
    }

    val deps = setOf(
      ContentModuleName("intellij.libraries.assertj.core"),  // NOT in model - should be unresolved
      ContentModuleName("intellij.platform.ide.core"),       // in module set - OK
      ContentModuleName("intellij.platform.vcs.impl"),       // in plugin content - OK
    )

    val unresolved = graph.query {
      val query = createResolutionQuery()
      query.findUnresolvedDeps(deps, existsAnywhere, "")
    }

    assertThat(unresolved).containsExactly(ContentModuleName("intellij.libraries.assertj.core"))
  }

  @Test
  fun `findUnresolvedDeps returns empty when all deps have sources`() {
    val graph = pluginGraph {
      product("TestProduct") {
        bundlesPlugin("intellij.platform.vcs")
        includesModuleSet("ide.common")
      }
      plugin("intellij.platform.vcs") { content("intellij.platform.vcs.impl") }
      moduleSet("ide.common") { module("intellij.platform.ide.core") }
    }

    val deps = setOf(ContentModuleName("intellij.platform.ide.core"), ContentModuleName("intellij.platform.vcs.impl"))
    val unresolved = graph.query {
      val query = createResolutionQuery()
      query.findUnresolvedDeps(deps, existsAnywhere, "")
    }

    assertThat(unresolved).isEmpty()
  }

  @Test
  fun `forProductionPlugin predicate accepts bundled test plugin modules`() {
    // Test plugins bundled in product ARE accepted by forProductionPlugin.
    // The prod/test boundary is enforced via validation warnings, not resolution failures.
    val graph = pluginGraph {
      product("TestProduct") {
        bundlesPlugin("intellij.featuresTrainer")
        bundlesTestPlugin("intellij.rdct.tests")
        includesModuleSet("ide.common")
      }
      testPlugin("intellij.rdct.tests") {
        content("intellij.libraries.assertj.core")
        content("intellij.platform.testFramework")
      }
      plugin("intellij.featuresTrainer") { content("intellij.vcs.git.featuresTrainer") }
      moduleSet("ide.common") { module("intellij.platform.ide.core") }
    }

    // Production plugin predicate accepts ALL bundled plugins (including test)
    val pluginDeps = setOf(ContentModuleName("intellij.libraries.assertj.core"), ContentModuleName("intellij.platform.ide.core"))
    val unresolved = graph.query {
      val query = createResolutionQuery()
      query.findUnresolvedDeps(pluginDeps, forProductionPlugin, "TestProduct")
    }

    // assertj.core is in test plugin bundled in TestProduct, so it resolves
    assertThat(unresolved).isEmpty()
  }

  @Test
  fun `forProductionPlugin predicate rejects non-bundled test plugin modules`() {
    // Test plugins NOT bundled in product are rejected
    val graph = pluginGraph {
      product("TestProduct") {
        bundlesPlugin("intellij.featuresTrainer")
        includesModuleSet("ide.common")
        // Note: rdct.tests is NOT bundled in TestProduct
      }
      testPlugin("intellij.rdct.tests") {
        content("intellij.libraries.assertj.core")
        content("intellij.platform.testFramework")
      }
      plugin("intellij.featuresTrainer") { content("intellij.vcs.git.featuresTrainer") }
      moduleSet("ide.common") { module("intellij.platform.ide.core") }
    }

    val pluginDeps = setOf(ContentModuleName("intellij.libraries.assertj.core"), ContentModuleName("intellij.platform.ide.core"))
    val unresolved = graph.query {
      val query = createResolutionQuery()
      query.findUnresolvedDeps(pluginDeps, forProductionPlugin, "TestProduct")
    }

    // assertj.core is in test plugin NOT bundled in TestProduct, so it's unresolved
    assertThat(unresolved).containsExactly(ContentModuleName("intellij.libraries.assertj.core"))
  }

  @Test
  fun `forProductionPlugin predicate accepts production plugin modules`() {
    val graph = pluginGraph {
      product("TestProduct") {
        bundlesPlugin("intellij.featuresTrainer")
        bundlesPlugin("intellij.platform.vcs")
        includesModuleSet("ide.common")
      }
      plugin("intellij.featuresTrainer") { content("intellij.vcs.git.featuresTrainer") }
      plugin("intellij.platform.vcs") { content("intellij.platform.vcs.impl") }
      moduleSet("ide.common") { module("intellij.platform.ide.core") }
    }

    val pluginDeps = setOf(ContentModuleName("intellij.platform.vcs.impl"), ContentModuleName("intellij.platform.ide.core"))
    val unresolved = graph.query {
      val query = createResolutionQuery()
      query.findUnresolvedDeps(pluginDeps, forProductionPlugin, "TestProduct")
    }

    assertThat(unresolved).isEmpty()
  }

  // --- Duplicate content module deduplication test ---

  @Test
  fun `duplicate content modules across plugins generates single diff`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        plugin("plugin.one") { content("intellij.shared.content") }
        plugin("plugin.two") { content("intellij.shared.content") }
        contentModule("intellij.shared.content") {
          descriptor = """<idea-plugin package="com.intellij.shared"/>"""
        }
      }

      val result = setup.generateDependencies(listOf("plugin.one", "plugin.two"))

      // Verify: both plugins should have the content module in results
      assertThat(result.files).hasSize(2)
      for (pluginResult in result.files) {
        val contentModuleNames = pluginResult.contentModuleResults.map { it.contentModuleName }
        assertThat(contentModuleNames)
          .describedAs("Plugin ${pluginResult.pluginContentModuleName} should have shared content module in results")
          .contains(ContentModuleName("intellij.shared.content"))
      }

      // Verify: only ONE diff for the shared content module descriptor (deduplication fix)
      val contentModuleDiffs = setup.strategy.getDiffs().filter {
        it.path.toString().contains("intellij.shared.content.xml")
      }
      assertThat(contentModuleDiffs)
        .describedAs("Should have at most one diff for shared content module (was duplicated before fix)")
        .hasSizeLessThanOrEqualTo(1)
    }
  }

  // --- ON_DEMAND deps with productAllowedMissing test ---

  @Test
  fun `ON_DEMAND content module deps allowed by productAllowedMissing do not trigger error`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        plugin("intellij.test.plugin") {
          content("intellij.test.content", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.ON_DEMAND)
        }
        contentModule("intellij.test.content") {
          descriptor = """<idea-plugin package="com.intellij.test"/>"""
          jpsDependency("intellij.platform.commercial.verifier")
        }
        product("TestProduct") {
          bundlesPlugin("intellij.test.plugin")
        }
      }

      // The dependency IS in productAllowedMissing
      val result = setup.generateDependencies(
        plugins = listOf("intellij.test.plugin"),
        productAllowedMissing = mapOf(
          "TestProduct" to setOf(ContentModuleName("intellij.platform.commercial.verifier")),
        ),
      )

      // Verify: NO validation errors - the ON_DEMAND dep is allowed by productAllowedMissing
      assertThat(result.errors)
        .describedAs("ON_DEMAND dep in productAllowedMissing should not trigger error")
        .isEmpty()
    }
  }

  @Test
  fun `ON_DEMAND content module deps NOT in productAllowedMissing trigger error`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        plugin("intellij.test.plugin") {
          content("intellij.test.content", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.ON_DEMAND)
        }
        // Declare dependency module FIRST so it has a descriptor when jpsDependency processes it
        // NOT in any plugin content (unresolvable), but has descriptor so it's validated
        contentModule("intellij.unknown.module") {
          descriptor = """<idea-plugin package="com.intellij.unknown"/>"""
        }
        contentModule("intellij.test.content") {
          descriptor = """<idea-plugin package="com.intellij.test"/>"""
          jpsDependency("intellij.unknown.module")
        }
        product("TestProduct") {
          bundlesPlugin("intellij.test.plugin")
        }
      }

      // productAllowedMissing does NOT include the unknown module
      val result = setup.generateDependencies(
        plugins = listOf("intellij.test.plugin"),
        productAllowedMissing = mapOf(
          "TestProduct" to setOf(ContentModuleName("intellij.some.other.module")),
        ),
      )

      // Verify: validation error for unknown ON_DEMAND dep
      val pluginError = result.errors.filterIsInstance<PluginDependencyError>().firstOrNull()
      assertThat(pluginError)
        .describedAs("Unknown ON_DEMAND dep should trigger error")
        .isNotNull()
      assertThat(pluginError!!.missingDependencies.keys)
        .contains(ContentModuleName("intellij.unknown.module"))
    }
  }

  // --- Filtered JPS dependencies tracking test ---

  @Test
  fun `filtered JPS dependencies are tracked and reported in error message`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        plugin("intellij.test.plugin") { content("intellij.test.content") }
        // Declare dependency module FIRST so it has a descriptor when jpsDependency processes it
        contentModule("intellij.some.filtered.dep") {
          descriptor = """<idea-plugin package="com.intellij.filtered"/>"""
        }
        contentModule("intellij.test.content") {
          descriptor = """<idea-plugin package="com.intellij.test"/>"""
          jpsDependency("intellij.some.filtered.dep")
        }
        product("TestProduct") {
          bundlesPlugin("intellij.test.plugin")
        }
      }

      val result = setup.generateDependencies(
        plugins = listOf("intellij.test.plugin"),
        suppressionConfig = SuppressionConfig(
          contentModules = mapOf(
            ContentModuleName("intellij.test.content") to ContentModuleSuppression(
              suppressModules = setOf(ContentModuleName("intellij.some.filtered.dep"))
            )
          )
        ),
      )

      // Verify: error contains filteredDependencies
      val pluginError = result.errors.filterIsInstance<PluginDependencyError>().firstOrNull()
      assertThat(pluginError).isNotNull()
      assertThat(pluginError!!.filteredDependencies)
        .containsKey(ContentModuleName("intellij.test.content"))
      assertThat(pluginError.filteredDependencies.get(ContentModuleName("intellij.test.content")))
        .contains(ContentModuleName("intellij.some.filtered.dep"))

      // Verify: error message shows filtered status
      val errorMessage = pluginError.format(AnsiStyle(false))
      assertThat(errorMessage)
        .contains("auto-inferred JPS dependency, filtered by config")
        .contains("pluginAllowedMissingDependencies")
    }
  }

  // --- Non-bundled plugin validation test ---

  @Test
  fun `non-bundled plugin with unresolvable module dependency triggers error`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        plugin("intellij.settingsRepository") {
          // Plugin has module dependency but no content modules
          moduleDependency("intellij.libraries.sshd.osgi")  // Not declared as <content> anywhere
        }
        // Not bundled in any product
      }

      val result = setup.generateDependencies(listOf("intellij.settingsRepository"))

      // Verify: validation error for unknown module dependency
      val pluginError = result.errors.filterIsInstance<PluginDependencyError>().firstOrNull()
      assertThat(pluginError)
        .describedAs("Non-bundled plugin with unresolvable module dependency should trigger error")
        .isNotNull()
      assertThat(pluginError!!.missingDependencies.keys)
        .contains(ContentModuleName("intellij.libraries.sshd.osgi"))

      // Verify: error message suggests fix for non-bundled plugin
      val errorMessage = pluginError.format(AnsiStyle(false))
      assertThat(errorMessage)
        .contains("not bundled in any product")
        .contains("declare it as <content>")
    }
  }

  @Test
  fun `non-bundled plugin with resolvable module dependency passes validation`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        plugin("intellij.settingsRepository") {
          moduleDependency("intellij.some.known.module")
        }
        // Declare the dependency as content in another plugin
        plugin("intellij.other.plugin") {
          content("intellij.some.known.module")
        }
        contentModule("intellij.some.known.module") {
          descriptor = """<idea-plugin package="com.intellij.some"/>"""
        }
      }

      val result = setup.generateDependencies(listOf("intellij.settingsRepository", "intellij.other.plugin"))

      // Verify: NO validation errors - the dependency exists in allKnownModules
      assertThat(result.errors)
        .describedAs("Non-bundled plugin with resolvable module dependency should pass validation")
        .isEmpty()
    }
  }

  // --- Test-only dependency detection tests ---

  @Test
  fun `non-bundled plugin depending on test-only content module triggers error`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      // Use a custom test framework module that we create in the test setup
      val localTestFrameworkModules = setOf(ContentModuleName("test.framework.marker.module"))

      val setup = pluginTestSetup(tempDir) {
        // Test plugin (contains test framework module in content, making it a test plugin)
        plugin("intellij.python.junit5Tests.plugin") {
          content("test.framework.marker.module", "intellij.libraries.sshd.osgi")
        }
        contentModule("test.framework.marker.module") {
          descriptor = """<idea-plugin package="com.test.framework"/>"""
        }
        contentModule("intellij.libraries.sshd.osgi") {
          descriptor = """<idea-plugin package="com.intellij.sshd"/>"""
        }
        // Production plugin (depends on module only available in test plugin)
        plugin("intellij.settingsRepository") {
          moduleDependency("intellij.libraries.sshd.osgi")
        }
        // Not bundled in any product
      }

      val result = setup.generateDependencies(
        plugins = listOf("intellij.settingsRepository", "intellij.python.junit5Tests.plugin"),
        testFrameworkContentModules = localTestFrameworkModules,  // Makes junit5Tests.plugin a test plugin
      )

      // Verify: validation error for test-only dependency
      val pluginError = result.errors.filterIsInstance<PluginDependencyError>()
        .firstOrNull { it.pluginName == TargetName("intellij.settingsRepository") }
      assertThat(pluginError)
        .describedAs("Non-bundled plugin depending on test-only content should trigger error")
        .isNotNull()
      assertThat(pluginError!!.missingDependencies.keys)
        .contains(ContentModuleName("intellij.libraries.sshd.osgi"))

      // Verify: error message shows test plugin source
      val errorMessage = pluginError.format(AnsiStyle(false))
      assertThat(errorMessage)
        .contains("only available in test plugin")
        .contains("intellij.python.junit5Tests.plugin")
    }
  }

  @Test
  fun `non-bundled plugin depending on module in both test and production plugins passes`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      // Use a custom test framework module that we create in the test setup
      val localTestFrameworkModules = setOf(ContentModuleName("test.framework.marker.module"))

      val setup = pluginTestSetup(tempDir) {
        // Test plugin (contains test framework module)
        plugin("intellij.test.plugin") {
          content("test.framework.marker.module", "intellij.shared.module")
        }
        contentModule("test.framework.marker.module") {
          descriptor = """<idea-plugin package="com.test.framework"/>"""
        }
        // Production plugin also declares the same module
        plugin("intellij.production.plugin") {
          content("intellij.shared.module")
        }
        contentModule("intellij.shared.module") {
          descriptor = """<idea-plugin package="com.intellij.shared"/>"""
        }
        // Another production plugin depends on the shared module
        plugin("intellij.consumer.plugin") {
          moduleDependency("intellij.shared.module")
        }
      }

      val result = setup.generateDependencies(
        plugins = listOf("intellij.consumer.plugin", "intellij.test.plugin", "intellij.production.plugin"),
        testFrameworkContentModules = localTestFrameworkModules,
      )

      // Verify: NO error - module has at least one non-test source (production.plugin)
      val consumerErrors = result.errors.filterIsInstance<PluginDependencyError>()
        .filter { it.pluginName == TargetName("intellij.consumer.plugin") }
      assertThat(consumerErrors)
        .describedAs("Module available in both test and production plugins should pass validation")
        .isEmpty()
    }
  }

  @Test
  fun `non-bundled plugin depending on module set module passes validation`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        // Production plugin depends on a module
        plugin("intellij.consumer.plugin") {
          moduleDependency("intellij.platform.ide.core")
        }
        // Module is available via module set
        product("TestProduct") {
          moduleSet("ide.common") {
            module("intellij.platform.ide.core")
          }
        }
      }

      val result = setup.generateDependencies(listOf("intellij.consumer.plugin"))

      // Verify: NO error - module is available from module set
      val consumerErrors = result.errors.filterIsInstance<PluginDependencyError>()
        .filter { it.pluginName == TargetName("intellij.consumer.plugin") }
      assertThat(consumerErrors)
        .describedAs("Module available in module set should pass validation for non-bundled plugin")
        .isEmpty()
    }
  }

  // --- DSL test plugin auto-add missing dependencies test ---
  // This test verifies that computePluginContentFromDslSpec auto-adds JPS deps with module descriptors.
  // See: PyCharmProperties.kt testPlugin { ... } where dependencies like intellij.platform.jewel.intUi.standalone
  // should be automatically added because they're JPS dependencies with module descriptors.

  @Test
  fun `computePluginContentFromDslSpec auto-adds JPS deps with module descriptors`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      // Setup: Create JPS modules with dependencies
      val jps = jpsProject(tempDir) {
        // Content module explicitly declared in DSL
        module("intellij.python.processOutput.impl") {
          resourceRoot()
          // Has JPS dependency on jewel module
          moduleDep("intellij.platform.jewel.intUi.standalone")
        }
        // Dependency module with module descriptor - should be auto-added
        module("intellij.platform.jewel.intUi.standalone") {
          resourceRoot()
        }
      }

      // Create module descriptor XML for the dependency module
      val jewelResourcesDir = tempDir.resolve("intellij/platform/jewel/intUi/standalone/resources")
      java.nio.file.Files.createDirectories(jewelResourcesDir)
      java.nio.file.Files.writeString(
        jewelResourcesDir.resolve("intellij.platform.jewel.intUi.standalone.xml"),
        """<idea-plugin package="org.jetbrains.jewel.intui.standalone"/>"""
      )

      // Also create descriptor for the content module (required for DSL)
      val processOutputResourcesDir = tempDir.resolve("intellij/python/processOutput/impl/resources")
      java.nio.file.Files.createDirectories(processOutputResourcesDir)
      java.nio.file.Files.writeString(
        processOutputResourcesDir.resolve("intellij.python.processOutput.impl.xml"),
        """<idea-plugin package="com.intellij.python.processOutput"/>"""
      )

      // Create DSL spec - ONLY declares content module, NOT the dependency
      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("intellij.python.junit5Tests.plugin"),
        name = "Python Tests Plugin",
        pluginXmlPath = "python/junit5Tests/plugin/testResources/META-INF/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          requiredModule("intellij.python.processOutput.impl")
          // Note: intellij.platform.jewel.intUi.standalone is NOT declared here
          // It should be auto-added by computePluginContentFromDslSpec
        }
      )

      // Call the REAL function (graph is the source of truth for descriptor existence)
      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        moduleWithScopedDeps("intellij.python.processOutput.impl", "intellij.platform.jewel.intUi.standalone" to "COMPILE")
        product("TestProduct") { }
      }
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = ErrorSink(),
        descriptorCache = descriptorCache,
      )

      // Verify: dependency module with descriptor is auto-added to contentModules
      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("computePluginContentFromDslSpec should auto-add JPS deps with module descriptors")
        .contains(ContentModuleName("intellij.python.processOutput.impl"))  // explicitly declared
        .contains(ContentModuleName("intellij.platform.jewel.intUi.standalone"))  // auto-added
    }
  }

  @Test
  fun `computePluginContentFromDslSpec remaps JPS deps to test descriptor modules when only _test descriptor exists`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.consumer.module") {
          resourceRoot()
          moduleDep("intellij.platform.testFramework.junit5.wsl")
        }
        module("intellij.platform.testFramework.junit5.wsl") {
          resourceRoot()
        }
      }

      val consumerResourcesDir = tempDir.resolve("intellij/consumer/module/resources")
      java.nio.file.Files.createDirectories(consumerResourcesDir)
      java.nio.file.Files.writeString(
        consumerResourcesDir.resolve("intellij.consumer.module.xml"),
        """<idea-plugin package="com.intellij.consumer.module"/>"""
      )

      val wslResourcesDir = tempDir.resolve("intellij/platform/testFramework/junit5/wsl/resources")
      java.nio.file.Files.createDirectories(wslResourcesDir)
      java.nio.file.Files.writeString(
        wslResourcesDir.resolve("intellij.platform.testFramework.junit5.wsl._test.xml"),
        """<idea-plugin package="com.intellij.testFramework.junit5.wsl._test"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          module("intellij.consumer.module")
        }
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        moduleWithScopedDeps("intellij.consumer.module", "intellij.platform.testFramework.junit5.wsl" to "COMPILE")
        product("TestProduct") { }
      }
      val errorSink = ErrorSink()
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = errorSink,
        descriptorCache = descriptorCache,
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("JPS deps should resolve to *_test module when only *_test descriptor exists")
        .contains(ContentModuleName("intellij.consumer.module"))
        .contains(ContentModuleName("intellij.platform.testFramework.junit5.wsl._test"))
        .doesNotContain(ContentModuleName("intellij.platform.testFramework.junit5.wsl"))
      assertThat(errorSink.getErrors()).isEmpty()
    }
  }

  @Test
  fun `computePluginContentFromDslSpec keeps base module when both base and _test descriptors exist`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.consumer.module") {
          resourceRoot()
          moduleDep("intellij.platform.testFramework.junit5.wsl")
        }
        module("intellij.platform.testFramework.junit5.wsl") {
          resourceRoot()
        }
      }

      val consumerResourcesDir = tempDir.resolve("intellij/consumer/module/resources")
      java.nio.file.Files.createDirectories(consumerResourcesDir)
      java.nio.file.Files.writeString(
        consumerResourcesDir.resolve("intellij.consumer.module.xml"),
        """<idea-plugin package="com.intellij.consumer.module"/>"""
      )

      val wslResourcesDir = tempDir.resolve("intellij/platform/testFramework/junit5/wsl/resources")
      java.nio.file.Files.createDirectories(wslResourcesDir)
      java.nio.file.Files.writeString(
        wslResourcesDir.resolve("intellij.platform.testFramework.junit5.wsl.xml"),
        """<idea-plugin package="com.intellij.testFramework.junit5.wsl"/>"""
      )
      java.nio.file.Files.writeString(
        wslResourcesDir.resolve("intellij.platform.testFramework.junit5.wsl._test.xml"),
        """<idea-plugin package="com.intellij.testFramework.junit5.wsl._test"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          module("intellij.consumer.module")
        }
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        moduleWithScopedDeps("intellij.consumer.module", "intellij.platform.testFramework.junit5.wsl" to "COMPILE")
        product("TestProduct") { }
      }
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = ErrorSink(),
        descriptorCache = descriptorCache,
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("Base descriptor should be preferred when both base and *_test descriptors exist")
        .contains(ContentModuleName("intellij.consumer.module"))
        .contains(ContentModuleName("intellij.platform.testFramework.junit5.wsl"))
        .doesNotContain(ContentModuleName("intellij.platform.testFramework.junit5.wsl._test"))
    }
  }

  @Test
  fun `computePluginContentFromDslSpec auto-adds test descriptor deps`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.foo") {
          resourceRoot()
        }
        module("intellij.bar") {
          resourceRoot()
        }
      }

      val fooResourcesDir = tempDir.resolve("intellij/foo/resources")
      java.nio.file.Files.createDirectories(fooResourcesDir)
      java.nio.file.Files.writeString(
        fooResourcesDir.resolve("intellij.foo._test.xml"),
        """
          <idea-plugin package="com.intellij.foo._test">
            <dependencies>
              <module name="intellij.bar._test"/>
            </dependencies>
          </idea-plugin>
        """.trimIndent()
      )

      val barResourcesDir = tempDir.resolve("intellij/bar/resources")
      java.nio.file.Files.createDirectories(barResourcesDir)
      java.nio.file.Files.writeString(
        barResourcesDir.resolve("intellij.bar._test.xml"),
        """<idea-plugin package="com.intellij.bar._test"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          module("intellij.foo._test")
        }
      )

      coroutineScope {
        val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
        val graph = pluginGraphWithDescriptors(descriptorCache) {
          target("intellij.foo")
          target("intellij.bar")
          product("TestProduct") { }
        }
        val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
          testPluginSpec = spec,
          projectRoot = tempDir,
          resolvableModules = emptySet(),
          productName = "TestProduct",
          pluginGraph = graph,
          errorSink = ErrorSink(),
          descriptorCache = descriptorCache,
        )

        val contentModuleNames = result.contentModules.map { it.name }
        assertThat(contentModuleNames)
          .describedAs("computePluginContentFromDslSpec should auto-add test descriptor deps")
          .contains(ContentModuleName("intellij.foo._test"))
          .contains(ContentModuleName("intellij.bar._test"))
      }
    }
  }

  @Test
  fun `computePluginContentFromDslSpec ignores suppressed module deps for optional modules with content source`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.test.module") {
          resourceRoot()
          moduleDep("intellij.dep.with.descriptor")
        }
        module("intellij.dep.with.descriptor") {
          resourceRoot()
        }
      }

      val contentDir = tempDir.resolve("intellij/test/module/resources")
      java.nio.file.Files.createDirectories(contentDir)
      java.nio.file.Files.writeString(
        contentDir.resolve("intellij.test.module.xml"),
        """<idea-plugin package="com.intellij.test.module"/>"""
      )

      val depDir = tempDir.resolve("intellij/dep/with/descriptor/resources")
      java.nio.file.Files.createDirectories(depDir)
      java.nio.file.Files.writeString(
        depDir.resolve("intellij.dep.with.descriptor.xml"),
        """<idea-plugin package="com.intellij.dep.with.descriptor"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          module("intellij.test.module")
        }
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        plugin("intellij.dep.plugin") {
          content("intellij.dep.with.descriptor")
        }
        moduleWithScopedDeps("intellij.test.module", "intellij.dep.with.descriptor" to "COMPILE")
        moduleWithScopedDeps("intellij.dep.with.descriptor")
        product("TestProduct") { }
      }
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = ErrorSink(),
        descriptorCache = descriptorCache,
        suppressionConfig = SuppressionConfig(
          contentModules = mapOf(
            ContentModuleName("intellij.test.module") to ContentModuleSuppression(
              suppressModules = setOf(ContentModuleName("intellij.dep.with.descriptor"))
            )
          )
        ),
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("Suppressed JPS module deps with a content source should not be auto-added")
        .contains(ContentModuleName("intellij.test.module"))
        .doesNotContain(ContentModuleName("intellij.dep.with.descriptor"))
    }
  }

  @Test
  fun `computePluginContentFromDslSpec auto-adds suppressed module deps without content source`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.test.module") {
          resourceRoot()
          moduleDep("intellij.dep.with.descriptor")
        }
        module("intellij.dep.with.descriptor") {
          resourceRoot()
        }
      }

      val contentDir = tempDir.resolve("intellij/test/module/resources")
      java.nio.file.Files.createDirectories(contentDir)
      java.nio.file.Files.writeString(
        contentDir.resolve("intellij.test.module.xml"),
        """<idea-plugin package="com.intellij.test.module"/>"""
      )

      val depDir = tempDir.resolve("intellij/dep/with/descriptor/resources")
      java.nio.file.Files.createDirectories(depDir)
      java.nio.file.Files.writeString(
        depDir.resolve("intellij.dep.with.descriptor.xml"),
        """<idea-plugin package="com.intellij.dep.with.descriptor"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          module("intellij.test.module")
        }
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        moduleWithScopedDeps("intellij.test.module", "intellij.dep.with.descriptor" to "COMPILE")
        moduleWithScopedDeps("intellij.dep.with.descriptor")
        product("TestProduct") { }
      }
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = ErrorSink(),
        descriptorCache = descriptorCache,
        suppressionConfig = SuppressionConfig(
          contentModules = mapOf(
            ContentModuleName("intellij.test.module") to ContentModuleSuppression(
              suppressModules = setOf(ContentModuleName("intellij.dep.with.descriptor"))
            )
          )
        ),
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("Suppressed JPS module deps without content source should be auto-added")
        .contains(ContentModuleName("intellij.test.module"))
        .contains(ContentModuleName("intellij.dep.with.descriptor"))
    }
  }

  @Test
  fun `computePluginContentFromDslSpec reports suppressed plugin-owned deps for required modules`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.test.module") {
          resourceRoot()
          moduleDep("intellij.java.impl")
        }
        module("intellij.java.impl") {
          resourceRoot()
        }
      }

      val contentDir = tempDir.resolve("intellij/test/module/resources")
      java.nio.file.Files.createDirectories(contentDir)
      java.nio.file.Files.writeString(
        contentDir.resolve("intellij.test.module.xml"),
        """<idea-plugin package="com.intellij.test.module"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          requiredModule("intellij.test.module")
        }
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        plugin("intellij.java.plugin") {
          content("intellij.java.impl")
        }
        moduleWithScopedDeps("intellij.test.module", "intellij.java.impl" to "COMPILE")
        product("TestProduct") { }
      }

      val errorSink = ErrorSink()
      org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = errorSink,
        descriptorCache = descriptorCache,
        suppressionConfig = SuppressionConfig(
          contentModules = mapOf(
            ContentModuleName("intellij.test.module") to ContentModuleSuppression(
              suppressModules = setOf(ContentModuleName("intellij.java.impl"))
            )
          )
        ),
      )

      val errors = errorSink.getErrors().filterIsInstance<DslTestPluginDependencyError>()
      assertThat(errors)
        .describedAs("Suppressed plugin-owned deps from required modules should still be validated")
        .hasSize(1)
      assertThat(errors[0].contentModuleDependencyId)
        .isEqualTo(ContentModuleName("intellij.java.impl"))
    }
  }

  @Test
  fun `computePluginContentFromDslSpec records suppression usage for unresolved plugin-owned deps in updateSuppressions`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.test.module") {
          resourceRoot()
          moduleDep("intellij.java.impl")
        }
        module("intellij.java.impl") {
          resourceRoot()
        }
      }

      val contentDir = tempDir.resolve("intellij/test/module/resources")
      java.nio.file.Files.createDirectories(contentDir)
      java.nio.file.Files.writeString(
        contentDir.resolve("intellij.test.module.xml"),
        """<idea-plugin package="com.intellij.test.module"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          requiredModule("intellij.test.module")
        }
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        plugin("intellij.java.plugin") {
          content("intellij.java.impl")
        }
        moduleWithScopedDeps("intellij.test.module", "intellij.java.impl" to "COMPILE")
        product("TestProduct") { }
      }

      val errorSink = ErrorSink()
      val suppressionUsages = mutableListOf<SuppressionUsage>()
      org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = errorSink,
        descriptorCache = descriptorCache,
        updateSuppressions = true,
        suppressionUsageSink = suppressionUsages,
      )

      assertThat(errorSink.getErrors())
        .describedAs("updateSuppressions should not emit errors for unresolved plugin-owned deps")
        .isEmpty()
      assertThat(suppressionUsages)
        .contains(SuppressionUsage(ContentModuleName("intellij.test.module"), "intellij.java.impl", SuppressionType.MODULE_DEP))
    }
  }

  @Test
  fun `computePluginContentFromDslSpec auto-adds library module deps`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        library("JUnit4")
        module("intellij.libraries.junit4") {
          resourceRoot()
          libraryDep("JUnit4", exported = true)
        }
        module("intellij.test.module") {
          resourceRoot()
          libraryDep("JUnit4")
        }
      }

      val libraryResourcesDir = tempDir.resolve("intellij/libraries/junit4/resources")
      java.nio.file.Files.createDirectories(libraryResourcesDir)
      java.nio.file.Files.writeString(
        libraryResourcesDir.resolve("intellij.libraries.junit4.xml"),
        """<idea-plugin package="com.intellij.libraries.junit4"/>"""
      )

      val contentDir = tempDir.resolve("intellij/test/module/resources")
      java.nio.file.Files.createDirectories(contentDir)
      java.nio.file.Files.writeString(
        contentDir.resolve("intellij.test.module.xml"),
        """<idea-plugin package="com.intellij.test.module"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          requiredModule("intellij.test.module")
        }
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        moduleWithScopedDeps("intellij.test.module", "intellij.libraries.junit4" to "COMPILE")
        moduleWithScopedDeps("intellij.libraries.junit4")
        product("TestProduct") { }
      }
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = ErrorSink(),
        descriptorCache = descriptorCache,
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("Library deps should resolve to library modules and be auto-added")
        .contains(ContentModuleName("intellij.test.module"))
        .contains(ContentModuleName("intellij.libraries.junit4"))
    }
  }

  @Test
  fun `computePluginContentFromDslSpec auto-adds library modules even when owned by plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.libraries.junit4") {
          resourceRoot()
        }
        module("intellij.test.module") {
          resourceRoot()
          moduleDep("intellij.libraries.junit4")
        }
      }

      val libraryResourcesDir = tempDir.resolve("intellij/libraries/junit4/resources")
      java.nio.file.Files.createDirectories(libraryResourcesDir)
      java.nio.file.Files.writeString(
        libraryResourcesDir.resolve("intellij.libraries.junit4.xml"),
        """<idea-plugin package="com.intellij.libraries.junit4"/>"""
      )

      val contentDir = tempDir.resolve("intellij/test/module/resources")
      java.nio.file.Files.createDirectories(contentDir)
      java.nio.file.Files.writeString(
        contentDir.resolve("intellij.test.module.xml"),
        """<idea-plugin package="com.intellij.test.module"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          requiredModule("intellij.test.module")
        }
      )

      val errorSink = ErrorSink()
      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        moduleWithScopedDeps("intellij.test.module", "intellij.libraries.junit4" to "COMPILE")
        product("TestProduct") { }
        plugin("intellij.owner.plugin") { content("intellij.libraries.junit4") }
      }
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = errorSink,
        descriptorCache = descriptorCache,
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("Library modules should be auto-added even when owned by a plugin")
        .contains(ContentModuleName("intellij.test.module"))
        .contains(ContentModuleName("intellij.libraries.junit4"))
      assertThat(errorSink.getErrors()).isEmpty()
    }
  }

  @Test
  fun `computePluginContentFromDslSpec does NOT auto-add resolvable JPS deps with module descriptors`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.content.module") {
          resourceRoot()
          moduleDep("intellij.dep.resolvable")
        }
        module("intellij.dep.resolvable") {
          resourceRoot()
        }
      }

      val depResourcesDir = tempDir.resolve("intellij/dep/resolvable/resources")
      java.nio.file.Files.createDirectories(depResourcesDir)
      java.nio.file.Files.writeString(
        depResourcesDir.resolve("intellij.dep.resolvable.xml"),
        """<idea-plugin package="com.intellij.dep.resolvable"/>"""
      )

      val contentDir = tempDir.resolve("intellij/content/module/resources")
      java.nio.file.Files.createDirectories(contentDir)
      java.nio.file.Files.writeString(
        contentDir.resolve("intellij.content.module.xml"),
        """<idea-plugin package="com.intellij.content"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          requiredModule("intellij.content.module")
        }
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        moduleWithScopedDeps("intellij.content.module", "intellij.dep.resolvable" to "COMPILE")
        product("TestProduct") { }
      }
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = setOf(ContentModuleName("intellij.dep.resolvable")),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = ErrorSink(),
        descriptorCache = descriptorCache,
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("Resolvable deps should not be auto-added")
        .contains(ContentModuleName("intellij.content.module"))
        .doesNotContain(ContentModuleName("intellij.dep.resolvable"))
    }
  }

  @Test
  fun `computePluginContentFromDslSpec does NOT auto-add modules owned by bundled production plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.owner.module") {
          resourceRoot()
        }
        module("intellij.test.content") {
          resourceRoot()
          moduleDep("intellij.owner.module")
        }
      }

      val ownerResourcesDir = tempDir.resolve("intellij/owner/module/resources")
      java.nio.file.Files.createDirectories(ownerResourcesDir)
      java.nio.file.Files.writeString(
        ownerResourcesDir.resolve("intellij.owner.module.xml"),
        """<idea-plugin package="com.intellij.owner"/>"""
      )

      val contentResourcesDir = tempDir.resolve("intellij/test/content/resources")
      java.nio.file.Files.createDirectories(contentResourcesDir)
      java.nio.file.Files.writeString(
        contentResourcesDir.resolve("intellij.test.content.xml"),
        """<idea-plugin package="com.intellij.test.content"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("intellij.test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          requiredModule("intellij.test.content")
        }
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        product("TestProduct") { bundlesPlugin("intellij.owner.plugin") }
        plugin("intellij.owner.plugin") { content("intellij.owner.module") }
        moduleWithScopedDeps("intellij.test.content", "intellij.owner.module" to "COMPILE")
      }
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = ErrorSink(),
        descriptorCache = descriptorCache,
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("Bundled production plugin-owned modules should not be auto-added as test plugin content")
        .contains(ContentModuleName("intellij.test.content"))
        .doesNotContain(ContentModuleName("intellij.owner.module"))
    }
  }

  @Test
  fun `computePluginContentFromDslSpec does NOT auto-add modules owned by additional bundled plugins`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.owner.module") {
          resourceRoot()
        }
        module("intellij.test.content") {
          resourceRoot()
          moduleDep("intellij.owner.module")
        }
      }

      val ownerResourcesDir = tempDir.resolve("intellij/owner/module/resources")
      java.nio.file.Files.createDirectories(ownerResourcesDir)
      java.nio.file.Files.writeString(
        ownerResourcesDir.resolve("intellij.owner.module.xml"),
        """<idea-plugin package="com.intellij.owner"/>"""
      )

      val contentResourcesDir = tempDir.resolve("intellij/test/content/resources")
      java.nio.file.Files.createDirectories(contentResourcesDir)
      java.nio.file.Files.writeString(
        contentResourcesDir.resolve("intellij.test.content.xml"),
        """<idea-plugin package="com.intellij.test.content"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("intellij.test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          requiredModule("intellij.test.content")
        },
        additionalBundledPluginTargetNames = listOf(TargetName("intellij.owner.plugin")),
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        product("TestProduct") { }
        plugin("intellij.owner.plugin") { content("intellij.owner.module") }
        moduleWithScopedDeps("intellij.test.content", "intellij.owner.module" to "COMPILE")
      }
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = ErrorSink(),
        descriptorCache = descriptorCache,
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("Additional bundled plugin-owned modules should not be auto-added")
        .contains(ContentModuleName("intellij.test.content"))
        .doesNotContain(ContentModuleName("intellij.owner.module"))
    }
  }

  @Test
  fun `computePluginContentFromDslSpec auto-adds modules owned by bundled test plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.owner.module") {
          resourceRoot()
        }
        module("intellij.test.content") {
          resourceRoot()
          moduleDep("intellij.owner.module")
        }
      }

      val ownerResourcesDir = tempDir.resolve("intellij/owner/module/resources")
      java.nio.file.Files.createDirectories(ownerResourcesDir)
      java.nio.file.Files.writeString(
        ownerResourcesDir.resolve("intellij.owner.module.xml"),
        """<idea-plugin package="com.intellij.owner"/>"""
      )

      val contentResourcesDir = tempDir.resolve("intellij/test/content/resources")
      java.nio.file.Files.createDirectories(contentResourcesDir)
      java.nio.file.Files.writeString(
        contentResourcesDir.resolve("intellij.test.content.xml"),
        """<idea-plugin package="com.intellij.test.content"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("intellij.test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          requiredModule("intellij.test.content")
        }
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        product("TestProduct") { bundlesTestPlugin("intellij.owner.plugin") }
        testPlugin("intellij.owner.plugin") { content("intellij.owner.module") }
        moduleWithScopedDeps("intellij.test.content", "intellij.owner.module" to "COMPILE")
      }
      val errorSink = ErrorSink()
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = errorSink,
        descriptorCache = descriptorCache,
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("Modules owned by bundled test plugins should be auto-added")
        .contains(ContentModuleName("intellij.test.content"), ContentModuleName("intellij.owner.module"))

      val errors = errorSink.getErrors()
      assertThat(errors)
        .describedAs("Test-plugin ownership is ignored for auto-add and should not be an error")
        .isEmpty()
    }
  }

  @Test
  fun `computePluginContentFromDslSpec auto-adds modules owned by unbundled test plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.owner.module") {
          resourceRoot()
        }
        module("intellij.test.content") {
          resourceRoot()
          moduleDep("intellij.owner.module")
        }
      }

      val ownerResourcesDir = tempDir.resolve("intellij/owner/module/resources")
      java.nio.file.Files.createDirectories(ownerResourcesDir)
      java.nio.file.Files.writeString(
        ownerResourcesDir.resolve("intellij.owner.module.xml"),
        """<idea-plugin package="com.intellij.owner"/>"""
      )

      val contentResourcesDir = tempDir.resolve("intellij/test/content/resources")
      java.nio.file.Files.createDirectories(contentResourcesDir)
      java.nio.file.Files.writeString(
        contentResourcesDir.resolve("intellij.test.content.xml"),
        """<idea-plugin package="com.intellij.test.content"/>"""
      )

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("intellij.test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          requiredModule("intellij.test.content")
        }
      )

      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        product("TestProduct") { }
        testPlugin("intellij.owner.plugin") { content("intellij.owner.module") }
        moduleWithScopedDeps("intellij.test.content", "intellij.owner.module" to "COMPILE")
      }
      val errorSink = ErrorSink()
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = errorSink,
        descriptorCache = descriptorCache,
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("Unbundled test-plugin ownership should be ignored for auto-add")
        .contains(ContentModuleName("intellij.test.content"), ContentModuleName("intellij.owner.module"))

      val errors = errorSink.getErrors()
      assertThat(errors)
        .describedAs("Test-plugin ownership is ignored for auto-add and should not be an error")
        .isEmpty()
    }
  }

  @Test
  fun `computePluginContentFromDslSpec does NOT auto-add JPS deps without module descriptors`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      // Setup: Create JPS modules - one with descriptor, one without
      val jps = jpsProject(tempDir) {
        module("intellij.content.module") {
          resourceRoot()
          moduleDep("intellij.dep.with.descriptor")
          moduleDep("intellij.dep.without.descriptor")
        }
        module("intellij.dep.with.descriptor") {
          resourceRoot()
        }
        module("intellij.dep.without.descriptor") {
          // No resource root - so no module descriptor
        }
      }

      // Create descriptor ONLY for dep.with.descriptor
      val depWithDescriptorDir = tempDir.resolve("intellij/dep/with/descriptor/resources")
      java.nio.file.Files.createDirectories(depWithDescriptorDir)
      java.nio.file.Files.writeString(
        depWithDescriptorDir.resolve("intellij.dep.with.descriptor.xml"),
        """<idea-plugin package="com.intellij.dep.with"/>"""
      )

      // Content module descriptor
      val contentDir = tempDir.resolve("intellij/content/module/resources")
      java.nio.file.Files.createDirectories(contentDir)
      java.nio.file.Files.writeString(
        contentDir.resolve("intellij.content.module.xml"),
        """<idea-plugin package="com.intellij.content"/>"""
      )

      // Note: intellij.dep.without.descriptor has NO descriptor file

      val spec = org.jetbrains.intellij.build.productLayout.TestPluginSpec(
        pluginId = PluginId("test.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test/plugin.xml",
        spec = org.jetbrains.intellij.build.productLayout.productModules {
          requiredModule("intellij.content.module")
        }
      )

      // Call the REAL function (graph is the source of truth for descriptor existence)
      val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
      val graph = pluginGraphWithDescriptors(descriptorCache) {
        moduleWithScopedDeps(
          "intellij.content.module",
          "intellij.dep.with.descriptor" to "COMPILE",
          "intellij.dep.without.descriptor" to "COMPILE",
        )
        product("TestProduct") { }
      }
      val result = org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec(
        testPluginSpec = spec,
        projectRoot = tempDir,
        resolvableModules = emptySet(),
        productName = "TestProduct",
        pluginGraph = graph,
        errorSink = ErrorSink(),
        descriptorCache = descriptorCache,
      )

      val contentModuleNames = result.contentModules.map { it.name }
      assertThat(contentModuleNames)
        .describedAs("Only deps WITH module descriptors should be auto-added")
        .contains(ContentModuleName("intellij.content.module"))  // explicitly declared
        .contains(ContentModuleName("intellij.dep.with.descriptor"))  // auto-added (has descriptor)
        .doesNotContain(ContentModuleName("intellij.dep.without.descriptor"))  // NOT auto-added (no descriptor)
    }
  }

  @Test
  fun `pluginAllowedMissingDependencies bypasses test-only check for non-bundled plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      // Use a custom test framework module that we create in the test setup
      val localTestFrameworkModules = setOf(ContentModuleName("test.framework.marker.module"))

      val setup = pluginTestSetup(tempDir) {
        // Test plugin with the target module
        plugin("intellij.test.plugin") {
          content("test.framework.marker.module", "intellij.target.module")
        }
        contentModule("test.framework.marker.module") {
          descriptor = """<idea-plugin package="com.test.framework"/>"""
        }
        contentModule("intellij.target.module") {
          descriptor = """<idea-plugin package="com.intellij.target"/>"""
        }
        // Production plugin depends on test-only module
        plugin("intellij.consumer.plugin") {
          moduleDependency("intellij.target.module")
        }
      }

      val result = setup.generateDependencies(
        plugins = listOf("intellij.consumer.plugin", "intellij.test.plugin"),
        testFrameworkContentModules = localTestFrameworkModules,
        pluginAllowedMissingDependencies = mapOf(
          TargetName("intellij.consumer.plugin") to setOf(ContentModuleName("intellij.target.module")),
        ),
      )

      // Verify: NO error - allowed by pluginAllowedMissingDependencies
      val consumerErrors = result.errors.filterIsInstance<PluginDependencyError>()
        .filter { it.pluginName == TargetName("intellij.consumer.plugin") }
      assertThat(consumerErrors)
        .describedAs("Test-only dep in pluginAllowedMissingDependencies should not trigger error")
        .isEmpty()
    }
  }

  // --- Globally embedded module filtering tests ---

  @Test
  fun `plugin dependency on globally embedded module is skipped`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      // Setup: Plugin depends on a module that is globally embedded (in EMBEDDED module set, no plugin source)
      val setup = pluginTestSetup(tempDir) {
        // Globally embedded module - in EMBEDDED module set, no plugin source
        contentModule("intellij.platform.core") {
          descriptor = """<idea-plugin package="com.intellij.core"/>"""
        }

        // Content module with JPS dependency on embedded module
        contentModule("intellij.my.content") {
          descriptor = """<idea-plugin package="com.intellij.content"/>"""
          jpsDependency("intellij.platform.core")
        }

        // Plugin with the content module
        plugin("intellij.my.plugin") {
          content("intellij.my.content")
        }

        // Product with EMBEDDED module set containing the dependency
        product("TestProduct") {
          bundlesPlugin("intellij.my.plugin")
          moduleSet("essential") {
            module("intellij.platform.core", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
          }
        }
      }

      val result = setup.generateDependencies(listOf("intellij.my.plugin"))

      // Verify: Plugin XML should NOT have the embedded module dependency
      val pluginResult = result.files.find { it.pluginContentModuleName.value == "intellij.my.plugin" }
      assertThat(pluginResult).isNotNull()

      val diffs = setup.strategy.getDiffs()
      val pluginXmlDiff = diffs.find { it.path.toString().contains("intellij.my.plugin") && it.path.toString().endsWith("plugin.xml") }

      if (pluginXmlDiff != null) {
        assertThat(pluginXmlDiff.expectedContent)
          .describedAs("Plugin XML should skip globally embedded module dependency")
        .doesNotContain("""<module name="intellij.platform.core"/>""")
      }
    }
  }

  @Test
  fun `plugin dependency embedded only in subset of products is kept`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        contentModule("intellij.platform.frontend.split") {
          descriptor = """<idea-plugin package="com.intellij.frontend.split"/>"""
        }

        contentModule("intellij.my.content") {
          descriptor = """<idea-plugin package="com.intellij.content"/>"""
          jpsDependency("intellij.platform.frontend.split")
        }

        plugin("intellij.my.plugin") {
          content("intellij.my.content")
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

      setup.generateDependencies(listOf("intellij.my.plugin"))

      val diffs = setup.strategy.getDiffs()
      val pluginXmlDiff = diffs.find { it.path.toString().contains("intellij.my.plugin") && it.path.toString().endsWith("plugin.xml") }

      if (pluginXmlDiff != null) {
        assertThat(pluginXmlDiff.expectedContent)
          .describedAs("Dependency must be kept when target is not globally embedded")
          .contains("""<module name="intellij.platform.frontend.split"/>""")
      }
    }
  }

  @Test
  fun `plugin dependency embedded in all bundled products is skipped`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        contentModule("intellij.platform.frontend.split") {
          descriptor = """<idea-plugin package="com.intellij.frontend.split"/>"""
        }

        contentModule("intellij.my.content") {
          descriptor = """<idea-plugin package="com.intellij.content"/>"""
          jpsDependency("intellij.platform.frontend.split")
        }

        plugin("intellij.my.plugin") {
          content("intellij.my.content")
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

      setup.generateDependencies(listOf("intellij.my.plugin"))

      val diffs = setup.strategy.getDiffs()
      val pluginXmlDiff = diffs.find { it.path.toString().contains("intellij.my.plugin") && it.path.toString().endsWith("plugin.xml") }

      if (pluginXmlDiff != null) {
        assertThat(pluginXmlDiff.expectedContent)
          .describedAs("Dependency should be skipped when embedded in all products where plugin is bundled")
          .doesNotContain("""<module name="intellij.platform.frontend.split"/>""")
      }
    }
  }

  @Test
  fun `plugin dependency is skipped when only CodeServer misses bundled owner`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        contentModule("intellij.platform.ide.impl") {
          descriptor = """<idea-plugin package="com.intellij.ide.impl"/>"""
        }

        contentModule("intellij.my.content") {
          descriptor = """<idea-plugin package="com.intellij.content"/>"""
          jpsDependency("intellij.platform.ide.impl")
        }

        plugin("intellij.platform.owner") {
          content("intellij.platform.ide.impl", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
        }

        plugin("intellij.my.plugin") {
          content("intellij.my.content")
        }

        product("CodeServer") {
          bundlesPlugin("intellij.my.plugin")
        }

        product("Idea") {
          bundlesPlugin("intellij.my.plugin")
          bundlesPlugin("intellij.platform.owner")
        }
      }

      setup.generateDependencies(listOf("intellij.my.plugin", "intellij.platform.owner"))

      val diffs = setup.strategy.getDiffs()
      val pluginXmlDiff = diffs.find { it.path.toString().contains("intellij.my.plugin") && it.path.toString().endsWith("plugin.xml") }

      if (pluginXmlDiff != null) {
        assertThat(pluginXmlDiff.expectedContent)
          .describedAs("CodeServer must be excluded from embedded-check scope")
          .doesNotContain("""<module name="intellij.platform.ide.impl"/>""")
      }
    }
  }

  @Test
  fun `plugin dependency on globally embedded module is skipped for non-bundled plugin`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        contentModule("intellij.platform.core") {
          descriptor = """<idea-plugin package="com.intellij.core"/>"""
        }

        contentModule("intellij.my.content") {
          descriptor = """<idea-plugin package="com.intellij.content"/>"""
          jpsDependency("intellij.platform.core")
        }

        plugin("intellij.my.plugin") {
          content("intellij.my.content")
        }

        // Plugin intentionally remains non-bundled.
        product("TestProduct") {
          moduleSet("essential") {
            module("intellij.platform.core", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.EMBEDDED)
          }
        }
      }

      setup.generateDependencies(listOf("intellij.my.plugin"))

      val diffs = setup.strategy.getDiffs()
      val pluginXmlDiff = diffs.find { it.path.toString().contains("intellij.my.plugin") && it.path.toString().endsWith("plugin.xml") }

      if (pluginXmlDiff != null) {
        assertThat(pluginXmlDiff.expectedContent)
          .describedAs("Non-bundled plugin should skip globally embedded dependency")
          .doesNotContain("""<module name="intellij.platform.core"/>""")
      }
    }
  }

  @Test
  fun `plugin dependency on module in another plugin is kept`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      // Setup: Plugin depends on a module that is in another plugin (NOT globally embedded)
      val setup = pluginTestSetup(tempDir) {
        // Module in another plugin - NOT globally embedded because it has a plugin source
        contentModule("intellij.vcs.core") {
          descriptor = """<idea-plugin package="com.intellij.vcs"/>"""
        }

        plugin("intellij.vcs.plugin") {
          content("intellij.vcs.core")
        }

        // Content module depending on the other plugin's content
        contentModule("intellij.my.content") {
          descriptor = """<idea-plugin package="com.intellij.content"/>"""
          jpsDependency("intellij.vcs.core")
        }

        plugin("intellij.my.plugin") {
          content("intellij.my.content")
        }

        product("TestProduct") {
          bundlesPlugin("intellij.my.plugin")
          bundlesPlugin("intellij.vcs.plugin")
        }
      }

      setup.generateDependencies(listOf("intellij.my.plugin", "intellij.vcs.plugin"))

      // Verify: Plugin XML should have the module dependency (it's in a plugin, not embedded)
      val diffs = setup.strategy.getDiffs()
      val pluginXmlDiff = diffs.find { it.path.toString().contains("intellij.my.plugin") && it.path.toString().endsWith("plugin.xml") }

      // Either no diff (dep already in XML) or diff contains the dependency
      if (pluginXmlDiff != null) {
        assertThat(pluginXmlDiff.expectedContent)
          .describedAs("Module in another plugin is NOT globally embedded, should be kept")
        .contains("""<module name="intellij.vcs.core"/>""")
      }
    }
  }

  @Test
  fun `plugin dependency on module with REQUIRED loading is kept`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      // Setup: Module in module set but with REQUIRED loading (not EMBEDDED)
      val setup = pluginTestSetup(tempDir) {
        contentModule("intellij.platform.optional") {
          descriptor = """<idea-plugin package="com.intellij.optional"/>"""
        }

        contentModule("intellij.my.content") {
          descriptor = """<idea-plugin package="com.intellij.content"/>"""
          jpsDependency("intellij.platform.optional")
        }

        plugin("intellij.my.plugin") {
          content("intellij.my.content")
        }

        product("TestProduct") {
          bundlesPlugin("intellij.my.plugin")
          moduleSet("optional.set") {
            // REQUIRED loading, not EMBEDDED - dependency should be kept
            module("intellij.platform.optional", com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue.REQUIRED)
          }
        }
      }

      setup.generateDependencies(listOf("intellij.my.plugin"))

      val diffs = setup.strategy.getDiffs()
      val pluginXmlDiff = diffs.find { it.path.toString().contains("intellij.my.plugin") && it.path.toString().endsWith("plugin.xml") }

      if (pluginXmlDiff != null) {
        assertThat(pluginXmlDiff.expectedContent)
          .describedAs("Module with REQUIRED loading is NOT globally embedded, should be kept")
        .contains("""<module name="intellij.platform.optional"/>""")
      }
    }
  }

  // --- NON_STANDARD_DESCRIPTOR_ROOT detection tests ---

  @Test
  fun `module descriptor with module value declaration does not trigger NON_STANDARD_DESCRIPTOR_ROOT`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      // Descriptor with <module value="..."/> (module ID declaration) should NOT trigger error
      // This matches intellij.regexp.xml which has <module value="com.intellij.modules.regexp"/>
      val setup = pluginTestSetup(tempDir) {
        plugin("test.plugin") { content("intellij.regexp") }
        contentModule("intellij.regexp") {
          descriptor = """
          |<idea-plugin visibility="public">
          |  <module value="com.intellij.modules.regexp"/>
          |  <extensions defaultExtensionNs="com.intellij">
          |    <fileType name="RegExp" implementationClass="org.intellij.lang.regexp.RegExpFileType"/>
          |  </extensions>
          |</idea-plugin>
        """.trimMargin()
        }
      }

      coroutineScope {
        val descriptorCache = ModuleDescriptorCache(setup.jps.outputProvider, this)
        val info = descriptorCache.getOrAnalyze("intellij.regexp")

        assertThat(info).isNotNull()
        assertThat(info!!.suppressibleError)
          .describedAs("<module value=...> should NOT trigger NON_STANDARD_DESCRIPTOR_ROOT")
          .isNull()
      }
    }
  }

  @Test
  fun `module descriptor with dependencies root element triggers NON_STANDARD_DESCRIPTOR_ROOT`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      // Descriptor with <dependencies> as root should trigger error (non-standard format)
      val setup = pluginTestSetup(tempDir) {
        plugin("test.plugin") { content("intellij.nonstandard") }
        contentModule("intellij.nonstandard") {
          descriptor = """
          |<dependencies>
          |  <module name="intellij.platform.ide"/>
          |  <plugin id="com.intellij.copyright"/>
          |</dependencies>
        """.trimMargin()
        }
      }

      coroutineScope {
        val descriptorCache = ModuleDescriptorCache(setup.jps.outputProvider, this)
        val info = descriptorCache.getOrAnalyze("intellij.nonstandard")

        assertThat(info).isNotNull()
        assertThat(info!!.suppressibleError)
          .describedAs("<dependencies> root should trigger NON_STANDARD_DESCRIPTOR_ROOT")
          .isNotNull()
        assertThat(info.suppressibleError!!.category)
          .isEqualTo(ErrorCategory.NON_STANDARD_DESCRIPTOR_ROOT)
      }
    }
  }

  @Test
  fun `module descriptor with idea-plugin root and dependencies section does not trigger error`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      // Standard descriptor with <idea-plugin> root and <dependencies> section should be fine
      val setup = pluginTestSetup(tempDir) {
        plugin("test.plugin") { content("intellij.standard") }
        contentModule("intellij.standard") {
          descriptor = """
          |<idea-plugin package="com.intellij.standard">
          |  <dependencies>
          |    <module name="intellij.platform.ide"/>
          |    <plugin id="com.intellij.copyright"/>
          |  </dependencies>
          |</idea-plugin>
        """.trimMargin()
        }
      }

      coroutineScope {
        val descriptorCache = ModuleDescriptorCache(setup.jps.outputProvider, this)
        val info = descriptorCache.getOrAnalyze("intellij.standard")

        assertThat(info).isNotNull()
        assertThat(info!!.suppressibleError)
          .describedAs("Standard <idea-plugin> with <dependencies> should NOT trigger error")
          .isNull()
        // Parser should have extracted dependencies
        assertThat(info.existingModuleDependencies).contains("intellij.platform.ide")
        assertThat(info.existingPluginDependencies).contains("com.intellij.copyright")
      }
    }
  }
}
