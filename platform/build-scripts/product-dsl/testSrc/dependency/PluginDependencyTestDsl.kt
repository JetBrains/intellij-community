// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")
@file:OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)

package org.jetbrains.intellij.build.productLayout.dependency

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import kotlinx.coroutines.GlobalScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.deps.PluginDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.discovery.ContentModuleInfo
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetGenerationConfig
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.discovery.PluginSource
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContextImpl
import org.jetbrains.intellij.build.productLayout.pipeline.ContentModuleOutput
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.DiscoveryResult
import org.jetbrains.intellij.build.productLayout.pipeline.ErrorSlot
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationMode
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationModel
import org.jetbrains.intellij.build.productLayout.pipeline.ModuleSetsOutput
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.PluginXmlOutput
import org.jetbrains.intellij.build.productLayout.pipeline.ProductModuleDepsOutput
import org.jetbrains.intellij.build.productLayout.pipeline.ProductsOutput
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.pipeline.SuppressionConfigOutput
import org.jetbrains.intellij.build.productLayout.pipeline.TestPluginDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.pipeline.TestPluginsOutput
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.jetbrains.intellij.build.productLayout.util.XmlWritePolicy
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.impl.JpsModuleSerializationDataExtensionImpl
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test DSL for building JPS projects, plugin models, and integration test setups.
 */
@DslMarker
annotation class JpsTestDsl

// ========== JPS Project DSL ==========

/**
 * Creates a JPS project with modules for testing.
 *
 * Example:
 * ```
 * val jps = jpsProject(tempDir) {
 *   library("JUnit4")
 *   module("intellij.libraries.junit4") { libraryDep("JUnit4", exported = true) }
 *   module("test.plugin") { libraryDep("JUnit4", scope = TEST) }
 * }
 * ```
 */
internal fun jpsProject(baseDir: Path, block: JpsProjectBuilder.() -> Unit): JpsProjectContext {
  val builder = JpsProjectBuilder(baseDir)
  builder.block()
  return builder.build()
}

@JpsTestDsl
class JpsProjectBuilder(private val baseDir: Path) {
  private val libraries = mutableListOf<String>()
  private val modules = mutableListOf<JpsModuleSpec>()

  fun library(name: String) {
    libraries.add(name)
  }

  fun module(name: String, block: JpsModuleBuilder.() -> Unit = {}) {
    val builder = JpsModuleBuilder(name)
    builder.block()
    modules.add(builder.build())
  }

  internal fun build(): JpsProjectContext {
    val model = JpsElementFactory.getInstance().createModel()
    val project = model.project

    // Create libraries
    val libraryMap = libraries.associateWith { name ->
      project.libraryCollection.addLibrary(name, JpsJavaLibraryType.INSTANCE)
    }

    // Create modules
    val moduleMap = LinkedHashMap<String, JpsModule>()
    for (spec in modules) {
      val moduleDir = spec.baseDir ?: baseDir.resolve(spec.name.replace('.', '/'))
      Files.createDirectories(moduleDir)

      val jpsModule = project.addModule(spec.name, JpsJavaModuleType.INSTANCE)
      jpsModule.container.setChild(
        JpsModuleSerializationDataExtensionImpl.ROLE,
        JpsModuleSerializationDataExtensionImpl(moduleDir),
      )

      // Write custom .iml content if provided
      spec.imlContent?.let { content ->
        Files.writeString(moduleDir.resolve("${spec.name}.iml"), content)
      }

      // Add resource root if specified
      spec.resourceRoot?.let { relativePath ->
        val resourceDir = moduleDir.resolve(relativePath)
        Files.createDirectories(resourceDir)
        jpsModule.addSourceRoot(resourceDir.toUri().toString(), JavaResourceRootType.RESOURCE)
      }

      moduleMap.put(spec.name, jpsModule)
    }

    // Add dependencies after all modules are created
    for (spec in modules) {
      val jpsModule = moduleMap.get(spec.name)!!

      for (libDep in spec.libraryDeps) {
        val library = libraryMap.get(libDep.name)
                      ?: error("Library '${libDep.name}' not declared. Add library(\"${libDep.name}\") to jpsProject block.")
        val dep = jpsModule.dependenciesList.addLibraryDependency(library)
        JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dep).apply {
          isExported = libDep.exported
          scope = libDep.scope
        }
      }

      for (modDep in spec.moduleDeps) {
        val depModule = moduleMap.get(modDep.name)
                        ?: error("Module '${modDep.name}' not declared. Add module(\"${modDep.name}\") to jpsProject block.")
        val dep = jpsModule.dependenciesList.addModuleDependency(depModule)
        JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dep).apply {
          isExported = modDep.exported
          scope = modDep.scope
        }
      }
    }

    return JpsProjectContext(
      project = project,
      outputProvider = createTestModuleOutputProvider(project),
    )
  }
}

@JpsTestDsl
class JpsModuleBuilder(private val name: String) {
  var imlContent: String? = null
  var baseDir: Path? = null
  var resourceRoot: String? = null
  internal val libraryDeps = mutableListOf<LibraryDep>()
  internal val moduleDeps = mutableListOf<ModuleDep>()

  fun libraryDep(name: String, scope: JpsJavaDependencyScope = JpsJavaDependencyScope.COMPILE, exported: Boolean = false) {
    libraryDeps.add(LibraryDep(name, scope, exported))
  }

  fun moduleDep(name: String, scope: JpsJavaDependencyScope = JpsJavaDependencyScope.COMPILE, exported: Boolean = false) {
    moduleDeps.add(ModuleDep(name, scope, exported))
  }

  fun resourceRoot(relativePath: String = "resources") {
    resourceRoot = relativePath
  }

  internal fun build(): JpsModuleSpec {
    return JpsModuleSpec(
      name = name,
      imlContent = imlContent,
      baseDir = baseDir,
      resourceRoot = resourceRoot,
      libraryDeps = libraryDeps.toList(),
      moduleDeps = moduleDeps.toList(),
    )
  }
}

internal data class JpsModuleSpec(
  @JvmField val name: String,
  @JvmField val imlContent: String?,
  @JvmField val baseDir: Path?,
  @JvmField val resourceRoot: String?,
  @JvmField val libraryDeps: List<LibraryDep>,
  @JvmField val moduleDeps: List<ModuleDep>,
)

internal data class LibraryDep(
  @JvmField val name: String,
  @JvmField val scope: JpsJavaDependencyScope,
  @JvmField val exported: Boolean,
)

internal data class ModuleDep(
  @JvmField val name: String,
  @JvmField val scope: JpsJavaDependencyScope,
  @JvmField val exported: Boolean,
)

internal class JpsProjectContext(
  val project: JpsProject,
  val outputProvider: ModuleOutputProvider,
)

// ========== Integration Test Setup DSL ==========

/**
 * Creates a complete test setup for plugin dependency generation tests.
 *
 * Example:
 * ```
 * val setup = pluginTestSetup(tempDir) {
 *   plugin("plugin.one") { content("intellij.shared.content") }
 *   plugin("plugin.two") { content("intellij.shared.content") }
 *   contentModule("intellij.shared.content") {
 *     descriptor = """<idea-plugin package="com.intellij.shared"/>"""
 *   }
 * }
 * ```
 */
internal fun pluginTestSetup(tempDir: Path, block: PluginTestSetupBuilder.() -> Unit): PluginTestSetupContext {
  val builder = PluginTestSetupBuilder(tempDir)
  builder.block()
  return builder.build()
}

@JpsTestDsl
class PluginTestSetupBuilder(private val tempDir: Path) {
  private val plugins = mutableListOf<TestPluginSpec>()
  private val contentModules = mutableListOf<TestContentModuleSpec>()
  private val products = mutableListOf<TestProductSpec>()

  fun plugin(name: String, block: TestPluginBuilder.() -> Unit = {}) {
    val builder = TestPluginBuilder(name)
    builder.block()
    plugins.add(builder.build())
  }

  fun contentModule(name: String, block: TestContentModuleBuilder.() -> Unit = {}) {
    val builder = TestContentModuleBuilder(name)
    builder.block()
    contentModules.add(builder.build())
  }

  fun product(name: String, block: TestProductBuilder.() -> Unit = {}) {
    val builder = TestProductBuilder(name)
    builder.block()
    products.add(builder.build())
  }

  internal fun build(): PluginTestSetupContext {
    val model = JpsElementFactory.getInstance().createModel()
    val project = model.project

    // Track JPS dependencies per content module
    val contentModuleJpsDeps = LinkedHashMap<String, List<String>>()

    // Create content modules first
    for (spec in contentModules) {
      val moduleDir = tempDir.resolve(spec.name.replace('.', '/'))
      val resourcesDir = moduleDir.resolve("resources")
      Files.createDirectories(resourcesDir)

      val jpsModule = project.addModule(spec.name, JpsJavaModuleType.INSTANCE)
      jpsModule.container.setChild(
        JpsModuleSerializationDataExtensionImpl.ROLE,
        JpsModuleSerializationDataExtensionImpl(moduleDir),
      )
      jpsModule.addSourceRoot(JpsPathUtil.pathToUrl(resourcesDir.toString()), JavaResourceRootType.RESOURCE)

      // Write descriptor XML
      Files.writeString(resourcesDir.resolve("${spec.name}.xml"), spec.descriptor)

      // Track JPS dependencies for this content module (just module names for plugin-level tracking)
      contentModuleJpsDeps.put(spec.name, spec.jpsDependencies.map { it.moduleName })

      // Add JPS dependencies to other modules with their specified scopes
      for (jpsDep in spec.jpsDependencies) {
        val depModule = project.modules.find { it.name == jpsDep.moduleName }
                        ?: project.addModule(jpsDep.moduleName, JpsJavaModuleType.INSTANCE).also { dep ->
                          // Create a stub JPS module for dependency resolution only.
                          // Do NOT create a descriptor file - if the dep is a plugin, it should not have {pluginName}.xml
                          // Only content modules (declared via contentModule()) should have descriptors.
                          val depDir = tempDir.resolve(jpsDep.moduleName.replace('.', '/'))
                          Files.createDirectories(depDir)
                          dep.container.setChild(
                            JpsModuleSerializationDataExtensionImpl.ROLE,
                            JpsModuleSerializationDataExtensionImpl(depDir),
                          )
                        }
        val moduleDep = jpsModule.dependenciesList.addModuleDependency(depModule)
        // Set the specified scope (COMPILE, TEST, etc.)
        JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(moduleDep).scope = jpsDep.scope
      }
    }

    // Create plugin modules
    val pluginContentInfos = LinkedHashMap<String, PluginContentInfo>()

    for (spec in plugins) {
      val pluginDir = tempDir.resolve(spec.name.replace('.', '/'))
      val metaInfDir = pluginDir.resolve("resources/META-INF")
      Files.createDirectories(metaInfDir)

      val jpsModule = project.addModule(spec.name, JpsJavaModuleType.INSTANCE)
      jpsModule.container.setChild(
        JpsModuleSerializationDataExtensionImpl.ROLE,
        JpsModuleSerializationDataExtensionImpl(pluginDir),
      )

      // Build plugin.xml content
      val contentSection = if (spec.contentModules.isNotEmpty()) {
        val contentElements = spec.contentModules.joinToString("\n    ") { "<module name=\"$it\"/>" }
        """
        |  <content>
        |    $contentElements
        |  </content>""".trimMargin()
      }
      else {
        ""
      }

      val dependenciesSection = if (spec.moduleDependencies.isNotEmpty()) {
        val depElements = spec.moduleDependencies.joinToString("\n    ") { "<module name=\"$it\"/>" }
        """
        |  <dependencies>
        |    $depElements
        |  </dependencies>""".trimMargin()
      }
      else {
        ""
      }

      val pluginXmlContent = buildString {
        appendLine("<idea-plugin>")
        if (contentSection.isNotEmpty()) appendLine(contentSection)
        if (dependenciesSection.isNotEmpty()) appendLine(dependenciesSection)
        append("</idea-plugin>")
      }
      val pluginXmlPath = metaInfDir.resolve("plugin.xml")
      Files.writeString(pluginXmlPath, pluginXmlContent)

      // Build List<ContentModuleInfo> from content modules and their loading modes
      val contentModuleInfos = spec.contentModules.map { moduleName ->
        ContentModuleInfo(name = ContentModuleName(moduleName), loadingMode = spec.contentLoadings.get(moduleName))
      }
      pluginContentInfos.put(spec.name, PluginContentInfo(
        pluginXmlPath = pluginXmlPath,
        pluginXmlContent = pluginXmlContent,
        pluginId = spec.pluginId?.let { PluginId(it) },
        contentModules = contentModuleInfos,
        moduleDependencies = spec.moduleDependencies.mapTo(HashSet()) { ContentModuleName(it) },
        source = if (spec.isTestPlugin) PluginSource.TEST else PluginSource.BUNDLED,
      ))

    }

    // Create stub cache with known plugins
    val stubCache = StubPluginContentCache(pluginContentInfos)

    // Build plugin graph from test setup data
    val graph = buildPluginGraphFromTestSetup(
      plugins = plugins,
      products = products,
      knownPlugins = pluginContentInfos,
      contentModuleSpecs = contentModules,
    )

    return PluginTestSetupContext(
      jps = JpsProjectContext(project, createTestModuleOutputProvider(project)),
      pluginContentInfos = pluginContentInfos,
      strategy = DeferredFileUpdater(tempDir),
      pluginContentCache = stubCache,
      products = products.toList(),
      pluginGraph = graph,
      contentModuleSpecs = contentModules,
    )
  }
}

@JpsTestDsl
class TestPluginBuilder(private val name: String) {
  /** Plugin ID from <id> element. May differ from module name. */
  var pluginId: String? = null

  /** If true, plugin dependencies are auto-derived from JPS deps. */
  var isTestPlugin: Boolean = false
  private val contentModules = LinkedHashSet<String>()
  private val contentLoadings = LinkedHashMap<String, com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue?>()
  private val moduleDependencies = LinkedHashSet<String>()

  fun content(vararg modules: String) {
    contentModules.addAll(modules)
  }

  fun content(moduleName: String, loading: com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue?) {
    contentModules.add(moduleName)
    contentLoadings.put(moduleName, loading)
  }

  /** Adds a module dependency to plugin.xml `<dependencies><module name="..."/>` section */
  fun moduleDependency(moduleName: String) {
    moduleDependencies.add(moduleName)
  }

  internal fun build(): TestPluginSpec {
    val effectivePluginId = pluginId ?: name
    return TestPluginSpec(
      name = name,
      pluginId = effectivePluginId,
      isTestPlugin = isTestPlugin,
      contentModules = contentModules.toSet(),
      contentLoadings = contentLoadings.toMap(),
      moduleDependencies = moduleDependencies.toSet(),
    )
  }
}

@JpsTestDsl
class TestContentModuleBuilder(private val name: String) {
  var descriptor: String = """<idea-plugin package="com.test"/>"""
  private val jpsDependencies = mutableListOf<TestJpsDependency>()

  fun jpsDependency(moduleName: String, scope: JpsJavaDependencyScope = JpsJavaDependencyScope.COMPILE) {
    jpsDependencies.add(TestJpsDependency(moduleName, scope))
  }

  internal fun build() = TestContentModuleSpec(name, descriptor, jpsDependencies.toList())
}

internal data class TestJpsDependency(
  @JvmField val moduleName: String,
  @JvmField val scope: JpsJavaDependencyScope,
)

@JpsTestDsl
class TestProductBuilder(private val name: String) {
  private val moduleSets = mutableListOf<TestModuleSetSpec>()
  private val bundledPlugins = LinkedHashSet<String>()
  private val testPlugins = LinkedHashSet<String>()

  fun moduleSet(name: String, block: TestModuleSetBuilder.() -> Unit = {}) {
    val builder = TestModuleSetBuilder(name)
    builder.block()
    moduleSets.add(builder.build())
  }

  fun bundlesPlugin(pluginName: String) {
    bundledPlugins.add(pluginName)
  }

  internal fun build() = TestProductSpec(name, moduleSets.toList(), bundledPlugins.toSet(), testPlugins.toSet())
}

@JpsTestDsl
class TestModuleSetBuilder(private val name: String) {
  private val modules = LinkedHashMap<String, com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue?>()

  fun module(moduleName: String, loading: com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue? = null) {
    modules.put(moduleName, loading)
  }

  internal fun build() = TestModuleSetSpec(name, modules.toMap())
}

internal data class TestPluginSpec(
  @JvmField val name: String,
  @JvmField val pluginId: String?,
  @JvmField val isTestPlugin: Boolean,
  @JvmField val contentModules: Set<String>,
  @JvmField val contentLoadings: Map<String, com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue?> = emptyMap(),
  @JvmField val moduleDependencies: Set<String> = emptySet(),
)

internal data class TestContentModuleSpec(
  @JvmField val name: String,
  @JvmField val descriptor: String,
  @JvmField val jpsDependencies: List<TestJpsDependency>,
)
internal data class TestProductSpec(
  @JvmField val name: String,
  @JvmField val moduleSets: List<TestModuleSetSpec>,
  @JvmField val bundledPlugins: Set<String>,
  @JvmField val testPlugins: Set<String>,
)

internal data class TestModuleSetSpec(
  @JvmField val name: String,
  @JvmField val modules: Map<String, com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue?>,
)

internal class PluginTestSetupContext(
  val jps: JpsProjectContext,
  val pluginContentInfos: Map<String, PluginContentInfo>,
  val strategy: DeferredFileUpdater,
  /** Stub cache for tests - pre-populated with plugin content infos, no on-demand extraction */
  val pluginContentCache: StubPluginContentCache,
  /** Products with their module sets for graph building */
  val products: List<TestProductSpec> = emptyList(),
  /** Pre-built plugin graph from test setup data */
  val pluginGraph: PluginGraph,
  /** Content module specs for graph rebuilding (needed for classifyTarget to find standalone content modules) */
  val contentModuleSpecs: List<TestContentModuleSpec> = emptyList(),
)

/**
 * Stub implementation of plugin content cache for tests.
 * Pre-populated with known plugins, returns null for unknown modules (no on-demand extraction).
 */
internal class StubPluginContentCache(
  private val knownPlugins: Map<String, PluginContentInfo>,
) : PluginContentProvider {
  override suspend fun getOrExtract(pluginModule: TargetName): PluginContentInfo? {
    return knownPlugins.get(pluginModule.value)
  }

  fun getKnownPlugins(): Map<String, PluginContentInfo> = knownPlugins
}

// ========== Shared Utilities ==========

/**
 * Build a [PluginGraph] from test setup data using the pluginGraph DSL.
 *
 * @param testFrameworkContentModules Optional set of content modules that indicate a test plugin.
 *        When non-empty, plugins containing any of these modules are marked as test plugins.
 */
internal fun buildPluginGraphFromTestSetup(
  plugins: List<TestPluginSpec>,
  products: List<TestProductSpec>,
  knownPlugins: Map<String, PluginContentInfo>,
  testFrameworkContentModules: Set<ContentModuleName> = emptySet(),
  contentModuleSpecs: List<TestContentModuleSpec> = emptyList(),
): PluginGraph {
  // Collect test plugin names from products
  val testPluginNames = products.flatMapTo(HashSet()) { it.testPlugins }

  return pluginGraph {
    // Add all declared content modules as NODE_CONTENT_MODULE vertices with their JPS deps.
    // This enables classifyTarget() to recognize standalone content modules as module dependencies
    // and creates EDGE_TARGET_DEPENDS_ON edges for their JPS dependencies.
    for (spec in contentModuleSpecs) {
      moduleWithScopedDeps(spec.name, *spec.jpsDependencies.map { it.moduleName to it.scope.name }.toTypedArray())
    }

    // Add products with bundled plugins and module sets
    for (product in products) {
      product(product.name) {
        for (plugin in product.bundledPlugins) {
          bundlesPlugin(plugin)
        }
        for (testPlugin in product.testPlugins) {
          bundlesTestPlugin(testPlugin)
        }
        for (moduleSetSpec in product.moduleSets) {
          includesModuleSet(moduleSetSpec.name)
        }
      }
    }

    // Add module sets from products
    val processedModuleSets = HashSet<String>()
    for (product in products) {
      for (moduleSetSpec in product.moduleSets) {
        if (processedModuleSets.add(moduleSetSpec.name)) {
          moduleSet(moduleSetSpec.name) {
            for ((moduleName, loading) in moduleSetSpec.modules) {
              if (loading != null) {
                module(moduleName, loading)
              }
              else {
                module(moduleName)
              }
            }
          }
        }
      }
    }

    // Add plugins with their content modules
    for ((pluginModuleName, pluginContent) in knownPlugins) {
      val contentModules = pluginContent.contentModules.mapTo(HashSet()) { it.name }

      // Check if plugin is a test plugin based on:
      // 1. Explicit test plugin in product
      // 2. TestPluginSpec.isTestPlugin flag from DSL
      // 3. Contains test framework content modules
      // 4. Name pattern match
      val isTestPlugin = pluginModuleName in testPluginNames ||
                         plugins.find { it.name == pluginModuleName }?.isTestPlugin == true ||
                         isTestPluginByContent(contentModules, testFrameworkContentModules) ||
                         isTestPluginByName(pluginModuleName)

      val pluginIdValue = pluginContent.pluginId?.value

      if (isTestPlugin) {
        testPlugin(pluginModuleName) {
          if (pluginIdValue != null) {
            pluginId(pluginIdValue)
          }
          for (module in pluginContent.contentModules) {
            val loading = module.loadingMode
                          ?: com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue.OPTIONAL
            content(module.name.value, loading)
          }
          for (moduleDep in pluginContent.moduleDependencies) {
            dependsOnContentModule(moduleDep.value)
          }
        }
      }
      else {
        plugin(pluginModuleName) {
          if (pluginIdValue != null) {
            pluginId(pluginIdValue)
          }
          for (module in pluginContent.contentModules) {
            val loading = module.loadingMode
                          ?: com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue.OPTIONAL
            content(module.name.value, loading)
          }
          for (moduleDep in pluginContent.moduleDependencies) {
            dependsOnContentModule(moduleDep.value)
          }
        }
      }

      // Add plugin's main target and JPS dependencies
      // This mirrors what ModelBuildingStage Phase 8 does in production:
      // Plugin --mainTarget--> Target --dependsOn--> Target
      // Get JPS deps from contentModuleSpecs for all content modules of this plugin
      val jpsDeps = pluginContent.contentModules
        .flatMap { contentModule ->
          contentModuleSpecs.find { it.name == contentModule.name.value }?.jpsDependencies?.map { it.moduleName } ?: emptyList()
        }

      // Create main target for plugin and add EDGE_MAIN_TARGET
      target(pluginModuleName) {
        for (dep in jpsDeps) {
          dependsOn(dep)
        }
      }
      linkPluginMainTarget(pluginModuleName)
    }
  }
}

private fun isTestPluginByContent(
  contentModules: Set<ContentModuleName>,
  testFrameworkContentModules: Set<ContentModuleName>,
): Boolean {
  return testFrameworkContentModules.isNotEmpty() && contentModules.any { it in testFrameworkContentModules }
}

private fun isTestPluginByName(pluginName: String): Boolean {
  return pluginName.endsWith(".testFramework") ||
         pluginName.contains(".testFramework.") ||
         pluginName.contains(".test.framework")
}

internal fun createTestModuleOutputProvider(project: JpsProject): ModuleOutputProvider {
  return object : ModuleOutputProvider {
    private val projectLibraryToModuleMapCache by lazy { buildTestProjectLibraryToModuleMap(project) }

    override fun findModule(name: String): JpsModule? = project.modules.find { it.name == name }

    override fun findRequiredModule(name: String): JpsModule {
      return findModule(name) ?: error("Module not found: $name")
    }

    override val useTestCompilationOutput: Boolean
      get() = true

    override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
      throw UnsupportedOperationException("Not needed for this test")
    }

    override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
      throw UnsupportedOperationException("Not needed for this test")
    }

    override fun getProjectLibraryToModuleMap(): Map<String, String> = projectLibraryToModuleMapCache

    override fun getAllModules(): List<JpsModule> = project.modules

    override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray {
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

private fun buildTestProjectLibraryToModuleMap(project: JpsProject): Map<String, String> {
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  val result = HashMap<String, String>()

  for (module in project.modules) {
    val moduleName = module.name
    if (!moduleName.startsWith(LIB_MODULE_PREFIX)) {
      continue
    }

    for (dep in module.dependenciesList.dependencies) {
      if (dep !is JpsLibraryDependency) {
        continue
      }

      val libRef = dep.libraryReference
      if (libRef.parentReference is JpsModuleReference) {
        continue
      }

      if (javaExtensionService.getDependencyExtension(dep)?.isExported != true) {
        continue
      }

      val libName = dep.library?.name ?: libRef.libraryName
      result.put(libName, moduleName)
    }
  }

  return result
}

// ========== Validation Rule Test Utilities ==========

/**
 * Creates a minimal [GenerationModel] for testing validation rules.
 *
 * Only populates fields used by validation rules:
 * - [GenerationModel.pluginGraph]
 *
 * Other fields are stubbed with empty/default values.
 */
internal fun testGenerationModel(
  pluginGraph: PluginGraph,
  outputProvider: ModuleOutputProvider? = null,
  fileUpdater: DeferredFileUpdater? = null,
  pluginContentCache: PluginContentCache? = null,
  suppressionConfig: SuppressionConfig = SuppressionConfig(),
  updateSuppressions: Boolean = false,
  pluginAllowedMissingDependencies: Map<ContentModuleName, Set<ContentModuleName>> = emptyMap(),
  testLibraryAllowedInModule: Map<ContentModuleName, Set<String>> = emptyMap(),
  productAllowedMissing: Map<String, Set<ContentModuleName>> = emptyMap(),
): GenerationModel {
  val effectiveOutputProvider = outputProvider ?: stubModuleOutputProvider()
  val effectiveFileUpdater = fileUpdater ?: DeferredFileUpdater(Path.of("."))
  val effectivePluginContentCache = pluginContentCache ?: stubPluginContentCache()
  val generationMode = if (updateSuppressions) GenerationMode.UPDATE_SUPPRESSIONS else GenerationMode.NORMAL
  return GenerationModel(
    discovery = DiscoveryResult(
      moduleSetsByLabel = emptyMap(),
      products = emptyList(),
      testProductSpecs = emptyList(),
      moduleSetSources = emptyMap(),
    ),
    config = ModuleSetGenerationConfig(
      moduleSetSources = emptyMap(),
      discoveredProducts = emptyList(),
      projectRoot = Path.of("."),
      outputProvider = effectiveOutputProvider,
      projectLibraryToModuleMap = effectiveOutputProvider.getProjectLibraryToModuleMap(),
      pluginAllowedMissingDependencies = pluginAllowedMissingDependencies,
      testLibraryAllowedInModule = testLibraryAllowedInModule,
    ),
    projectRoot = Path.of("."),
    outputProvider = effectiveOutputProvider,
    isUltimateBuild = false,
    descriptorCache = ModuleDescriptorCache(effectiveOutputProvider, GlobalScope),
    pluginContentCache = effectivePluginContentCache,
    fileUpdater = effectiveFileUpdater,
    xmlWritePolicy = XmlWritePolicy(generationMode, effectiveFileUpdater),
    scope = GlobalScope,
    pluginGraph = pluginGraph,
    dslTestPluginsByProduct = emptyMap(),
    dslTestPluginDependencyChains = emptyMap(),
    dslTestPluginSuppressionUsages = emptyList(),
    productAllowedMissing = productAllowedMissing,
    suppressionConfig = suppressionConfig,
    updateSuppressions = updateSuppressions,
    generationMode = generationMode,
  )
}

/**
 * Runs a validation rule and returns collected errors.
 *
 * Use this to test validation rules through the [PipelineNode] interface:
 * ```kotlin
 * val model = testGenerationModel(graph)
 * val errors = runValidationRule(SelfContainedModuleSetValidator, model)
 * ```
 *
 * Automatically initializes required slots with empty data for validation-only nodes.
 * Use slotOverrides to publish specific slot outputs instead of empty defaults.
 */
internal suspend fun runValidationRule(
  rule: PipelineNode,
  model: GenerationModel,
  slotOverrides: Map<DataSlot<*>, Any> = emptyMap(),
): List<ValidationError> {
  val ctx = ComputeContextImpl(model)

  // Initialize and publish empty data to required slots (for validation-only nodes)
  for (slot in rule.requires) {
    if (slot is ErrorSlot) continue
    ctx.initSlot(slot)
    @Suppress("UNCHECKED_CAST")
    when (slot) {
      Slots.CONTENT_MODULE -> {
        val output = when (val override = slotOverrides[slot]) {
          null -> ContentModuleOutput(files = emptyList())
          is ContentModuleOutput -> override
          else -> error("Slot override for CONTENT_MODULE must be ContentModuleOutput")
        }
        ctx.publish(slot as DataSlot<ContentModuleOutput>, output)
      }
      Slots.CONTENT_MODULE_PLAN -> {
        val output = when (val override = slotOverrides[slot]) {
          null -> ContentModuleDependencyPlanOutput(plans = emptyList())
          is ContentModuleDependencyPlanOutput -> override
          else -> error("Slot override for CONTENT_MODULE_PLAN must be ContentModuleDependencyPlanOutput")
        }
        ctx.publish(slot as DataSlot<ContentModuleDependencyPlanOutput>, output)
      }
      Slots.PLUGIN_DEPENDENCY_PLAN -> {
        val output = when (val override = slotOverrides[slot]) {
          null -> PluginDependencyPlanOutput(plans = emptyList())
          is PluginDependencyPlanOutput -> override
          else -> error("Slot override for PLUGIN_DEPENDENCY_PLAN must be PluginDependencyPlanOutput")
        }
        ctx.publish(slot as DataSlot<PluginDependencyPlanOutput>, output)
      }
      Slots.PLUGIN_XML -> {
        val output = when (val override = slotOverrides[slot]) {
          null -> PluginXmlOutput(files = emptyList(), detailedResults = emptyList())
          is PluginXmlOutput -> override
          else -> error("Slot override for PLUGIN_XML must be PluginXmlOutput")
        }
        ctx.publish(slot as DataSlot<PluginXmlOutput>, output)
      }
      Slots.MODULE_SETS -> {
        val output = when (val override = slotOverrides[slot]) {
          null -> ModuleSetsOutput(resultsByLabel = emptyList(), trackingMaps = emptyMap())
          is ModuleSetsOutput -> override
          else -> error("Slot override for MODULE_SETS must be ModuleSetsOutput")
        }
        ctx.publish(slot as DataSlot<ModuleSetsOutput>, output)
      }
      Slots.PRODUCT_MODULE_DEPS -> {
        val output = when (val override = slotOverrides[slot]) {
          null -> ProductModuleDepsOutput(files = emptyList())
          is ProductModuleDepsOutput -> override
          else -> error("Slot override for PRODUCT_MODULE_DEPS must be ProductModuleDepsOutput")
        }
        ctx.publish(slot as DataSlot<ProductModuleDepsOutput>, output)
      }
      Slots.PRODUCTS -> {
        val output = when (val override = slotOverrides[slot]) {
          null -> ProductsOutput(files = emptyList())
          is ProductsOutput -> override
          else -> error("Slot override for PRODUCTS must be ProductsOutput")
        }
        ctx.publish(slot as DataSlot<ProductsOutput>, output)
      }
      Slots.TEST_PLUGINS -> {
        val output = when (val override = slotOverrides[slot]) {
          null -> TestPluginsOutput(files = emptyList())
          is TestPluginsOutput -> override
          else -> error("Slot override for TEST_PLUGINS must be TestPluginsOutput")
        }
        ctx.publish(slot as DataSlot<TestPluginsOutput>, output)
      }
      Slots.TEST_PLUGIN_DEPENDENCY_PLAN -> {
        val output = when (val override = slotOverrides[slot]) {
          null -> TestPluginDependencyPlanOutput(plans = emptyList())
          is TestPluginDependencyPlanOutput -> override
          else -> error("Slot override for TEST_PLUGIN_DEPENDENCY_PLAN must be TestPluginDependencyPlanOutput")
        }
        ctx.publish(slot as DataSlot<TestPluginDependencyPlanOutput>, output)
      }
      Slots.SUPPRESSION_CONFIG -> {
        val output = when (val override = slotOverrides[slot]) {
          null -> SuppressionConfigOutput(moduleCount = 0, suppressionCount = 0, configModified = false)
          is SuppressionConfigOutput -> override
          else -> error("Slot override for SUPPRESSION_CONFIG must be SuppressionConfigOutput")
        }
        ctx.publish(slot as DataSlot<SuppressionConfigOutput>, output)
      }
    }
  }

  // Initialize produced slots (so the rule can publish to them)
  for (slot in rule.produces) {
    ctx.initSlot(slot)
  }

  val nodeCtx = ctx.forNode(rule.id)
  rule.execute(nodeCtx)
  ctx.finalizeNodeErrors(rule.id)
  return ctx.getNodeErrors(rule.id)
}

private fun stubModuleOutputProvider(): ModuleOutputProvider {
  return object : ModuleOutputProvider {
    override val useTestCompilationOutput: Boolean
      get() = true

    override fun findModule(name: String): JpsModule? = null
    override fun findRequiredModule(name: String): JpsModule = error("Module not found: $name")
    override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
      throw UnsupportedOperationException("Stub")
    }

    override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
      throw UnsupportedOperationException("Stub")
    }

    override fun getProjectLibraryToModuleMap(): Map<String, String> = emptyMap()

    override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray {
      throw UnsupportedOperationException("Stub")
    }

    override fun getModuleImlFile(module: JpsModule): Path {
      throw UnsupportedOperationException("Stub")
    }
  }
}

private fun stubPluginContentCache(): PluginContentCache {
  return PluginContentCache(
    outputProvider = stubModuleOutputProvider(),
    xIncludeCache = AsyncCache(GlobalScope),
    skipXIncludePaths = emptySet(),
    xIncludePrefixFilter = { null },
    scope = GlobalScope,
    errorSink = ErrorSink(),
  )
}
