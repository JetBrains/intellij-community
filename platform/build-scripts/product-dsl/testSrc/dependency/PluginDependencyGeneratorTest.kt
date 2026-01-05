// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.dependency

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.jetbrains.intellij.build.productLayout.validation.rules.validateLibraryModuleDependencies
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.impl.JpsModuleSerializationDataExtensionImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

// Test framework modules used in production configuration
private val testFrameworkModules = setOf(
  "intellij.libraries.junit4",
  "intellij.libraries.junit5",
  "intellij.libraries.junit5.jupiter",
  "intellij.platform.testFramework",
  "intellij.platform.testFramework.common",
  "intellij.platform.testFramework.core",
  "intellij.platform.testFramework.impl",
  "intellij.tools.testsBootstrap",
)

/**
 * Tests for PluginDependencyGenerator.kt functions.
 */
class PluginDependencyGeneratorTest {
  @Test
  fun `isTestPlugin returns true when content contains test framework module`() {
    val contentModules = setOf(
      "intellij.libraries.assertj.core",
      "intellij.platform.testFramework",
      "intellij.some.other.module",
    )

    val result = isTestPlugin(contentModules, testFrameworkModules)

    assertThat(result).isTrue()
  }

  @Test
  fun `isTestPlugin returns true when content contains junit module`() {
    val contentModules = setOf(
      "intellij.libraries.junit5",
      "intellij.libraries.assertj.core",
    )

    val result = isTestPlugin(contentModules, testFrameworkModules)

    assertThat(result).isTrue()
  }

  @Test
  fun `isTestPlugin returns false when content has no test framework modules`() {
    val contentModules = setOf(
      "intellij.platform.vcs.impl",
      "intellij.platform.ide.core",
      "intellij.libraries.assertj.core",
    )

    val result = isTestPlugin(contentModules, testFrameworkModules)

    assertThat(result).isFalse()
  }

  @Test
  fun `isTestPlugin returns false for empty content modules`() {
    val contentModules = emptySet<String>()

    val result = isTestPlugin(contentModules, testFrameworkModules)

    assertThat(result).isFalse()
  }

  @Test
  fun `isTestPlugin returns false when testFrameworkContentModules is empty`() {
    val contentModules = setOf(
      "intellij.platform.testFramework",
      "intellij.libraries.junit5",
    )

    val result = isTestPlugin(contentModules, emptySet())

    assertThat(result).isFalse()
  }

  // --- Validation bug regression test ---
  // Simulates the scenario where intellij.featuresTrainer depends on intellij.libraries.assertj.core
  // which is only available in test plugin content

  // --- computeProductionPluginModules() function tests ---

  @Test
  fun `computeProductionPluginModules excludes test plugin content`() {
    val pluginContentByPlugin = mapOf(
      // Test plugin - has test framework in content
      "intellij.rdct.tests" to setOf(
        "intellij.libraries.assertj.core",  // <-- only in test plugin
        "intellij.platform.testFramework",
        "intellij.libraries.junit5",
      ),
      // Production plugins - no test framework in content
      "intellij.featuresTrainer" to setOf(
        "intellij.vcs.git.featuresTrainer",
        "intellij.featuresTrainer.onboarding",
      ),
      "intellij.platform.vcs" to setOf(
        "intellij.platform.vcs.impl",
        "intellij.platform.vcs.log",
      ),
    )

    // REAL production function used by generatePluginDependencies()
    val result = computeProductionPluginModules(pluginContentByPlugin, testFrameworkModules)

    // Test plugin content should be excluded
    assertThat(result)
      .describedAs("Test plugin content should be excluded from production modules")
      .doesNotContain("intellij.libraries.assertj.core", "intellij.platform.testFramework", "intellij.libraries.junit5")

    // Production plugin content should be included
    assertThat(result)
      .describedAs("Production plugin content should be included")
      .contains(
        "intellij.vcs.git.featuresTrainer",
        "intellij.featuresTrainer.onboarding",
        "intellij.platform.vcs.impl",
        "intellij.platform.vcs.log",
      )
  }

  // --- findMissingDependencies() function tests ---

  @Test
  fun `findMissingDependencies identifies deps not available at runtime`() {
    val crossProductModules = setOf("intellij.platform.ide.core", "intellij.platform.tips")
    val productionPluginModules = setOf("intellij.vcs.git.featuresTrainer", "intellij.platform.vcs.impl")
    val deps = setOf(
      "intellij.libraries.assertj.core",  // NOT in either set - should be missing
      "intellij.platform.ide.core",       // in crossProductModules - OK
      "intellij.platform.vcs.impl",       // in productionPluginModules - OK
    )

    // REAL production function used by generatePluginDependencies()
    val missing = findMissingDependencies(deps, crossProductModules, productionPluginModules)

    assertThat(missing)
      .describedAs("Only assertj.core should be missing (not in cross-product or production plugin modules)")
      .containsExactly("intellij.libraries.assertj.core")
  }

  @Test
  fun `findMissingDependencies returns empty when all deps are available`() {
    val crossProductModules = setOf("intellij.platform.ide.core")
    val productionPluginModules = setOf("intellij.platform.vcs.impl")
    val deps = setOf("intellij.platform.ide.core", "intellij.platform.vcs.impl")

    val missing = findMissingDependencies(deps, crossProductModules, productionPluginModules)

    assertThat(missing).isEmpty()
  }

  // --- Full validation flow tests (using real functions from production code) ---

  @Test
  fun `full validation flow detects test-only dependency as missing`() {
    // This test exercises the SAME code path as generatePluginDependencies() validation

    // Setup: All plugins in the system (test + production)
    val pluginContentByPlugin = mapOf(
      // Test plugin - declares test framework modules as content
      "intellij.rdct.tests" to setOf(
        "intellij.libraries.assertj.core",  // <-- only available here
        "intellij.platform.testFramework",
        "intellij.libraries.junit5",
      ),
      // Production plugins
      "intellij.featuresTrainer" to setOf(
        "intellij.vcs.git.featuresTrainer",
        "intellij.featuresTrainer.onboarding",
      ),
      "intellij.platform.vcs" to setOf(
        "intellij.platform.vcs.impl",
        "intellij.platform.vcs.log",
      ),
    )

    val crossProductModules = setOf("intellij.platform.ide.core", "intellij.platform.tips")
    val pluginDeps = setOf("intellij.libraries.assertj.core", "intellij.platform.ide.core")

    // REAL production functions - same code path as generatePluginDependencies()
    val productionPluginModules =
      computeProductionPluginModules(pluginContentByPlugin, testFrameworkModules)
    val missing = findMissingDependencies(pluginDeps, crossProductModules, productionPluginModules)

    // assertj.core is missing because it's only in test plugin content
    assertThat(missing)
      .describedAs("assertj.core should be missing (only in test plugin content)")
      .containsExactly("intellij.libraries.assertj.core")
  }

  @Test
  fun `full validation flow allows deps from production plugin content`() {
    // Production plugins only - no test plugins
    val pluginContentByPlugin = mapOf(
      "intellij.featuresTrainer" to setOf(
        "intellij.vcs.git.featuresTrainer",
        "intellij.featuresTrainer.onboarding",
      ),
      "intellij.platform.vcs" to setOf(
        "intellij.platform.vcs.impl",
        "intellij.platform.vcs.log",
      ),
    )

    val crossProductModules = setOf("intellij.platform.ide.core")
    val pluginDeps = setOf("intellij.platform.vcs.impl", "intellij.platform.ide.core")

    val productionPluginModules = computeProductionPluginModules(pluginContentByPlugin, testFrameworkModules)
    val missing = findMissingDependencies(pluginDeps, crossProductModules, productionPluginModules)

    // Nothing should be missing - all deps are available
    assertThat(missing)
      .describedAs("All deps should be available (vcs.impl from production plugin, ide.core from cross-product)")
      .isEmpty()
  }

  // --- Library module validation integration test ---
  // Tests that plugin modules with direct library dependencies are caught by validation

  @Test
  fun `validateLibraryModuleDependencies detects direct library dependencies`(@TempDir tempDir: Path) {
    // 1. Create test .iml file with direct library dependency
    val imlContent = """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<module type="JAVA_MODULE" version="4">
      |  <component name="NewModuleRootManager">
      |    <orderEntry type="library" scope="TEST" name="JUnit4" level="project" />
      |  </component>
      |</module>
    """.trimMargin()
    Files.writeString(tempDir.resolve("test.plugin.iml"), imlContent)

    // 2. Create test JPS project with modules
    val model = JpsElementFactory.getInstance().createModel()
    val project = model.project

    // Create project library "JUnit4"
    val junit4Library = project.libraryCollection.addLibrary("JUnit4", JpsJavaLibraryType.INSTANCE)

    // Create library module that exports JUnit4
    val libraryModule = project.addModule("intellij.libraries.junit4", JpsJavaModuleType.INSTANCE)
    val libModuleDep = libraryModule.dependenciesList.addLibraryDependency(junit4Library)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(libModuleDep).apply {
      isExported = true
      scope = JpsJavaDependencyScope.COMPILE
    }

    // Create plugin module with DIRECT library dependency (the violation!)
    val pluginModule = project.addModule("test.plugin", JpsJavaModuleType.INSTANCE)
    pluginModule.container.setChild(
      JpsModuleSerializationDataExtensionImpl.ROLE,
      JpsModuleSerializationDataExtensionImpl(tempDir),
    )
    val pluginLibDep = pluginModule.dependenciesList.addLibraryDependency(junit4Library)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(pluginLibDep).apply {
      scope = JpsJavaDependencyScope.TEST
    }

    // 3. Create test ModuleOutputProvider
    val outputProvider = createTestModuleOutputProvider(project)

    // 4. Call validateLibraryModuleDependencies with strategy
    val strategy = DeferredFileUpdater(tempDir)
    validateLibraryModuleDependencies(
      modulesToCheck = setOf("intellij.libraries.junit4", "test.plugin"),
      outputProvider = outputProvider,
      strategy = strategy,
    )

    // 5. Verify diff is returned for the library dependency violation
    assertThat(strategy.getDiffs())
      .describedAs("Diff should be generated for library dependency violation")
      .hasSize(1)
  }

  @Test
  fun `validateLibraryModuleDependencies generates correct diff`(@TempDir tempDir: Path) {
    // 1. Create test .iml file with direct library dependency
    val imlContent = """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<module type="JAVA_MODULE" version="4">
      |  <component name="NewModuleRootManager">
      |    <orderEntry type="library" scope="TEST" name="JUnit4" level="project" />
      |  </component>
      |</module>
    """.trimMargin()
    Files.writeString(tempDir.resolve("test.plugin.iml"), imlContent)

    // 2. Create test JPS project with modules
    val model = JpsElementFactory.getInstance().createModel()
    val project = model.project

    // Create project library "JUnit4"
    val junit4Library = project.libraryCollection.addLibrary("JUnit4", JpsJavaLibraryType.INSTANCE)

    // Create library module that exports JUnit4
    val libraryModule = project.addModule("intellij.libraries.junit4", JpsJavaModuleType.INSTANCE)
    val libModuleDep = libraryModule.dependenciesList.addLibraryDependency(junit4Library)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(libModuleDep).apply {
      isExported = true
      scope = JpsJavaDependencyScope.COMPILE
    }

    // Create plugin module with DIRECT library dependency and set base directory
    val pluginModule = project.addModule("test.plugin", JpsJavaModuleType.INSTANCE)
    pluginModule.container.setChild(
      JpsModuleSerializationDataExtensionImpl.ROLE,
      JpsModuleSerializationDataExtensionImpl(tempDir),
    )
    val pluginLibDep = pluginModule.dependenciesList.addLibraryDependency(junit4Library)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(pluginLibDep).apply {
      scope = JpsJavaDependencyScope.TEST
    }

    // 3. Create test ModuleOutputProvider
    val outputProvider = createTestModuleOutputProvider(project)

    // 4. Call validateLibraryModuleDependencies with strategy
    val strategy = DeferredFileUpdater(tempDir)
    validateLibraryModuleDependencies(
      modulesToCheck = setOf("intellij.libraries.junit4", "test.plugin"),
      outputProvider = outputProvider,
      strategy = strategy,
    )

    // 5. Verify diff content is semantically correct
    val diffs = strategy.getDiffs()
    assertThat(diffs).hasSize(1)
    val diff = diffs.first()
    
    // actualContent is the current .iml content
    assertThat(diff.actualContent).isEqualTo(imlContent)
    
    // expectedContent is the fixed content
    val fixedContent = diff.expectedContent
    
    // Must contain module dependency
    assertThat(fixedContent)
      .describedAs("Fixed content must contain module dependency")
      .contains("module-name=\"intellij.libraries.junit4\"")
    
    // Must NOT contain the library dependency
    assertThat(fixedContent)
      .describedAs("Fixed content must not contain library dependency")
      .doesNotContain("name=\"JUnit4\"")
      .doesNotContain("type=\"library\"")
    
    // Must be valid XML (parse it to verify)
    val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val document = builder.parse(org.xml.sax.InputSource(java.io.StringReader(fixedContent)))
    
    // Verify the module element has correct structure
    val orderEntries = document.getElementsByTagName("orderEntry")
    assertThat(orderEntries.length)
      .describedAs("Should have exactly one orderEntry")
      .isEqualTo(1)
    
    val orderEntry = orderEntries.item(0) as org.w3c.dom.Element
    assertThat(orderEntry.getAttribute("type")).isEqualTo("module")
    assertThat(orderEntry.getAttribute("module-name")).isEqualTo("intellij.libraries.junit4")
  }

  // --- Duplicate content module deduplication test ---

  @Test
  fun `duplicate content modules across plugins generates single diff`(@TempDir tempDir: Path) {
    runBlocking {
    // 1. Create test JPS project with modules
    val model = JpsElementFactory.getInstance().createModel()
    val project = model.project

    // Shared content module declared in multiple plugins
    val sharedModuleDir = tempDir.resolve("shared")
    val sharedContentModule = project.addModule("intellij.shared.content", JpsJavaModuleType.INSTANCE)
    sharedContentModule.container.setChild(
      JpsModuleSerializationDataExtensionImpl.ROLE,
      JpsModuleSerializationDataExtensionImpl(sharedModuleDir),
    )

    // Create content source root for findFileInModuleSources to work
    val sharedResourcesDir = sharedModuleDir.resolve("resources")
    Files.createDirectories(sharedResourcesDir)
    val javaExtension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(sharedContentModule)
    sharedContentModule.addSourceRoot(sharedResourcesDir.toUri().toString(), org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE)

    // Create descriptor XML for content module (will generate diff since deps differ)
    Files.writeString(sharedResourcesDir.resolve("intellij.shared.content.xml"), """
      |<idea-plugin package="com.intellij.shared">
      |</idea-plugin>
    """.trimMargin())

    // Two plugin modules, both declaring shared content module
    val plugin1Dir = tempDir.resolve("plugin1")
    val plugin1 = project.addModule("plugin.one", JpsJavaModuleType.INSTANCE)
    plugin1.container.setChild(JpsModuleSerializationDataExtensionImpl.ROLE, JpsModuleSerializationDataExtensionImpl(plugin1Dir))

    val plugin2Dir = tempDir.resolve("plugin2")
    val plugin2 = project.addModule("plugin.two", JpsJavaModuleType.INSTANCE)
    plugin2.container.setChild(JpsModuleSerializationDataExtensionImpl.ROLE, JpsModuleSerializationDataExtensionImpl(plugin2Dir))

    // Create plugin.xml files
    Files.createDirectories(plugin1Dir.resolve("resources/META-INF"))
    Files.writeString(plugin1Dir.resolve("resources/META-INF/plugin.xml"), """
      |<idea-plugin>
      |  <content><module name="intellij.shared.content"/></content>
      |</idea-plugin>
    """.trimMargin())

    Files.createDirectories(plugin2Dir.resolve("resources/META-INF"))
    Files.writeString(plugin2Dir.resolve("resources/META-INF/plugin.xml"), """
      |<idea-plugin>
      |  <content><module name="intellij.shared.content"/></content>
      |</idea-plugin>
    """.trimMargin())

    // 2. Create test infrastructure
    val outputProvider = createTestModuleOutputProvider(project)
    val strategy = DeferredFileUpdater(tempDir)

    // Create PluginContentInfo for both plugins with shared content module
    val sharedContentSet = setOf("intellij.shared.content")
    val pluginContentJobs = mapOf(
      "plugin.one" to CompletableDeferred(PluginContentInfo(
        pluginXmlPath = plugin1Dir.resolve("resources/META-INF/plugin.xml"),
        pluginXmlContent = Files.readString(plugin1Dir.resolve("resources/META-INF/plugin.xml")),
        contentModules = sharedContentSet,
        jpsDependencies = { emptyList() },
      )),
      "plugin.two" to CompletableDeferred(PluginContentInfo(
        pluginXmlPath = plugin2Dir.resolve("resources/META-INF/plugin.xml"),
        pluginXmlContent = Files.readString(plugin2Dir.resolve("resources/META-INF/plugin.xml")),
        contentModules = sharedContentSet,
        jpsDependencies = { emptyList() },
      )),
    )

    val allPluginModules = AllPluginModules(
      allModules = sharedContentSet,
      byPlugin = mapOf("plugin.one" to sharedContentSet, "plugin.two" to sharedContentSet),
    )

    // 3. Call generatePluginDependencies
    kotlinx.coroutines.coroutineScope {
      val descriptorCache = ModuleDescriptorCache(outputProvider, this)
      val result = generatePluginDependencies(
        plugins = listOf("plugin.one", "plugin.two"),
        pluginContentJobs = pluginContentJobs,
        allPluginModulesDeferred = CompletableDeferred(allPluginModules),
        productIndicesDeferred = CompletableDeferred(emptyMap()),
        descriptorCache = descriptorCache,
        dependencyFilter = { _, _, _ -> true },
        strategy = strategy,
        testFrameworkContentModules = emptySet(),
      )

      // 4. Verify: both plugins should have the content module in results
      assertThat(result.files).hasSize(2)
      for (pluginResult in result.files) {
        val contentModuleNames = pluginResult.contentModuleResults.map { it.moduleName }
        assertThat(contentModuleNames)
          .describedAs("Plugin ${pluginResult.pluginModuleName} should have shared content module in results")
          .contains("intellij.shared.content")
      }
    }

    // 5. Verify: only ONE diff for the shared content module descriptor (deduplication fix)
    val contentModuleDiffs = strategy.getDiffs().filter {
      it.path.toString().contains("intellij.shared.content.xml")
    }
    assertThat(contentModuleDiffs)
      .describedAs("Should have at most one diff for shared content module (was duplicated before fix)")
      .hasSizeLessThanOrEqualTo(1)
    }
  }
}

private fun createTestModuleOutputProvider(project: JpsProject): ModuleOutputProvider {
  return object : ModuleOutputProvider {
    override fun findModule(name: String): JpsModule? = project.modules.find { it.name == name }

    override fun findRequiredModule(name: String): JpsModule = 
      findModule(name) ?: error("Module not found: $name")

    override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray {
      throw UnsupportedOperationException("Not needed for this test")
    }

    override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> =
      throw UnsupportedOperationException("Not needed for this test")

    override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> =
      throw UnsupportedOperationException("Not needed for this test")

    override suspend fun readFileContentFromModuleOutputAsync(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
      throw UnsupportedOperationException("Not needed for this test")
    }

    override fun getModuleImlFile(module: JpsModule): Path {
      val baseDir = requireNotNull(JpsModelSerializationDataService.getBaseDirectoryPath(module)) {
        "Cannot find base directory for module ${module.name}"
      }
      return baseDir.resolve("${module.name}.iml")
    }
  }
}
