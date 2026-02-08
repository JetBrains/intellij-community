// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * Programmatic content DSL for defining product module content in Kotlin instead of static XML.
 *
 * This file contains the core types for the programmatic content system, which allows products
 * to define their module composition, XML includes, and module sets using type-safe Kotlin code.
 *
 * For comprehensive documentation on how to use this system:
 * - [Programmatic Content](../docs/programmatic-content.md) - Complete guide with examples
 * - [Module Sets](../docs/module-sets.md) - How module sets work and best practices
 *
 * @see ProductModulesContentSpecBuilder
 */
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.serialization.Serializable

/**
 * Marker annotation for the product DSL to prevent implicit receiver scope leakage.
 * This ensures that methods from outer DSL scopes are not accidentally accessible in nested blocks.
 */
@DslMarker
annotation class ProductDslMarker

/**
 * Represents an XML include directive that references a resource within a module.
 *
 * @param contentModuleName The JPS module name containing the resource (e.g., "intellij.platform.resources")
 * @param resourcePath The path to the resource within the module (e.g., "META-INF/PlatformLangPlugin.xml")
 * @param ultimateOnly If true, this include is only processed in Ultimate builds (skipped in Community builds)
 * @param optional If true, this include is always generated with xi:fallback and never inlined (safe for files that may not exist)
 */
@Serializable
data class DeprecatedXmlInclude(
  val contentModuleName: ContentModuleName,
  @JvmField val resourcePath: String,
  @JvmField val ultimateOnly: Boolean = false,
  @JvmField val optional: Boolean = false,
)

/**
 * Tracks how a product spec is composed (e.g., via include(), moduleSet(), etc.).
 * This enables tracing module origins and detecting redundancies.
 */
@Serializable
data class SpecComposition(
  /** Type of composition (inline spec, module set reference, or deprecated XML include) */
  @JvmField val type: CompositionType,
  /** Reference name (module set name or function name) if applicable */
  @JvmField val reference: String? = null,
  /** Breadcrumb trail showing the composition path */
  @JvmField val path: List<String> = emptyList(),
  /** Source location where this composition was added (file:line) */
  @JvmField val sourceLocation: String? = null,
)

/**
 * Type of composition operation that added content to a product spec.
 */
@Serializable
enum class CompositionType {
  /** include(spec) - embedded content from another ProductModulesContentSpec */
  INLINE_SPEC,
  /** moduleSet(...) - reference to a module set */
  MODULE_SET_REF,
  /** deprecatedInclude(...) - reference to an XML file */
  DEPRECATED_XML,
  /** module() / embeddedModule() / requiredModule() - direct module addition */
  DIRECT_MODULE,
  /** alias() - module alias addition */
  ALIAS,
}

/**
 * Metadata about a product spec's origin for traceability.
 */
@Serializable
data class SpecMetadata(
  /** Name of the product or function that created this spec */
  @JvmField val name: String? = null,
  /** Source file path relative to project root */
  @JvmField val sourceFile: String? = null,
  /** Function name that created this spec */
  @JvmField val sourceFunction: String? = null,
)

/**
 * Specification for programmatically defining product content modules.
 * This allows products to specify module sets, individual modules, and XML includes in Kotlin code
 * instead of relying solely on XML xi:include directives.
 *
 * The content modules specified here will be automatically injected into the product's
 * plugin.xml during the build process via layout.withPatch.
 *
 * This class is immutable and should be constructed via the [productModules] DSL function.
 *
 * @see org.jetbrains.intellij.build.ProductProperties.getProductContentDescriptor
 */
@Serializable
class ProductModulesContentSpec(
  /**
   * Product plugin aliases for `<module value="..."/>` declarations (e.g., "com.jetbrains.gateway", "com.intellij.modules.idea").
   * These declare plugin aliases that can be used in `<depends>` or `<dependencies><plugin id="..."/>`.
   * Multiple aliases can be specified by calling alias() multiple times in the DSL.
   * @see <a href="../../docs/IntelliJ-Platform/4_man/Plugin-Model/Plugin-Model-v1-v2.md#plugin-aliases">Plugin Aliases Documentation</a>
   */
  @JvmField val productModuleAliases: List<PluginId>,

  /**
   * Product vendor name for the `<vendor>` tag in plugin.xml.
   * If null (default), no vendor tag will be generated.
   * Example: "JetBrains"
   */
  @JvmField val vendor: String? = null,

  /**
   * XML includes to add (xi:include directives).
   * These will be generated before module content blocks.
   * Each include specifies a module name and resource path within that module.
   */
  @JvmField val deprecatedXmlIncludes: List<DeprecatedXmlInclude>,

  /**
   * Module sets to include with optional loading overrides.
   * When a module set has overrides, it will be inlined in XML instead of using xi:include.
   */
  @JvmField val moduleSets: List<ModuleSetWithOverrides>,

  /**
   * Additional individual modules to include beyond those from module sets.
   * This allows fine-grained control to add specific modules not in any set.
   */
  @JvmField val additionalModules: List<ContentModule>,

  /**
   * List of bundled plugin JPS module names for dependency generation.
   * These are JPS modules that contain META-INF/plugin.xml.
   * Used ONLY for automatic dependency generation in plugin.xml files,
   * not for determining which plugins are bundled (that's done via productLayout.bundledPluginModules).
   */
  @JvmField val bundledPlugins: List<TargetName> = emptyList(),

  /**
   * JPS modules that are allowed to be missing during validation.
   * These are typically provided by plugin layouts rather than module sets.
   * Example: CIDR modules for CLion/AppCode that come from plugin bundles.
   */
  @JvmField val allowedMissingDependencies: Set<ContentModuleName> = emptySet(),

  /**
   * Composition graph tracking how this spec was assembled.
   * Records all include(), moduleSet(), and other composition operations.
   * Used for analysis, tracing, and redundancy detection.
   */
  @JvmField val compositionGraph: List<SpecComposition> = emptyList(),

  /**
   * Metadata about this spec's origin (product name, source file, function).
   * Useful for debugging and tracing where a spec was created.
   */
  @JvmField val metadata: SpecMetadata? = null,

  /**
   * Test plugins to generate programmatically.
   * Each spec defines a test plugin's ID, name, XML path, and content modules.
   * @see <a href="../test-plugins.md">Test Plugin Generation Documentation</a>
   */
  @JvmField val testPlugins: List<TestPluginSpec> = emptyList(),
)

/**
 * DSL builder for creating ProductModulesContentSpec with reduced boilerplate.
 */
@ProductDslMarker
class ProductModulesContentSpecBuilder @PublishedApi internal constructor() {
  private val productModuleAliases = mutableListOf<PluginId>()
  private var vendor: String? = null
  private val xmlIncludes = mutableListOf<DeprecatedXmlInclude>()
  private val moduleSets = mutableListOf<ModuleSetWithOverrides>()
  private val additionalModules = mutableListOf<ContentModule>()
  private val bundledPlugins = mutableListOf<TargetName>()
  private val allowedMissingDeps = LinkedHashSet<ContentModuleName>()
  private val testPlugins = mutableListOf<TestPluginSpec>()

  // Composition tracking
  private val compositionGraph = mutableListOf<SpecComposition>()
  private val pathStack = mutableListOf<String>() // Current path for nested compositions

  // Metadata for this spec
  internal var metadata: SpecMetadata? = null

  /**
   * Add a product plugin alias for `<module value="..."/>` declaration.
   * These declare plugin aliases that can be used in `<depends>` or `<dependencies><plugin id="..."/>`.
   * Can be called multiple times to add multiple aliases.
   * Example: alias("com.jetbrains.gateway")
   * Example: alias("com.intellij.modules.idea")
   */
  fun alias(value: String) {
    productModuleAliases.add(PluginId(value))
    compositionGraph.add(SpecComposition(
      type = CompositionType.ALIAS,
      reference = value,
      path = pathStack.toList(),
      sourceLocation = null // TODO: capture if needed
    ))
  }

  /**
   * Set the product vendor for the `<vendor>` tag in plugin.xml.
   * Example: vendor("JetBrains")
   */
  fun vendor(value: String) {
    this.vendor = value
  }

  /**
   * Include another ProductModulesContentSpec, merging all its contents into this builder.
   * This enables composition of product spec fragments for reuse across products.
   *
   * Example:
   * ```
   * override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
   *   include(commonCapabilityAliases())     // include spec fragment with common aliases
   *   include(platformCommonIncludes())      // include spec fragment with deprecatedIncludes
   *   moduleSet(commercialIdeBase())         // include module set
   * }
   * ```
   *
   * @param spec The ProductModulesContentSpec to merge into this builder
   */
  fun include(spec: ProductModulesContentSpec) {
    // Record composition before flattening (for analysis)
    compositionGraph.add(SpecComposition(
      type = CompositionType.INLINE_SPEC,
      reference = spec.metadata?.name ?: spec.metadata?.sourceFunction,
      path = pathStack.toList(),
      sourceLocation = spec.metadata?.sourceFile
    ))

    // Flatten content (existing behavior for backward compatibility)
    productModuleAliases.addAll(spec.productModuleAliases)
    if (spec.vendor != null) {
      vendor = spec.vendor
    }
    xmlIncludes.addAll(spec.deprecatedXmlIncludes)
    moduleSets.addAll(spec.moduleSets)
    additionalModules.addAll(spec.additionalModules)
    allowedMissingDeps.addAll(spec.allowedMissingDependencies)

    // Also preserve the nested spec's composition graph for deep analysis
    compositionGraph.addAll(spec.compositionGraph)
  }

  /**
   * Add an XML include (xi:include directive) by specifying module name and resource path.
   * Example: deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
   *
   * For Ultimate-only includes that should be conditionally processed:
   * Example: deprecatedInclude("intellij.platform.extended.community.impl", "META-INF/community-extensions.xml", ultimateOnly = true)
   *
   * For optional includes that may not exist in all builds (always uses xi:fallback):
   * Example: deprecatedInclude("intellij.rider.languages", "intellij.rider.languages.xml", optional = true)
   *
   * @param moduleName The JPS module name containing the resource
   * @param resourcePath The path to the resource within the module
   * @param ultimateOnly If true, this include is only processed in Ultimate builds.
   *   - When inlining: Skipped in Community builds
   *   - When NOT inlining: Generates xi:include with xi:fallback wrapper for graceful handling
   * @param optional If true, this include is never inlined and always generates xi:include with xi:fallback.
   *   Use this for includes that may not exist in all build configurations (e.g., Rider-specific XML files).
   *
   * @see <a href="programmatic-content.md#ultimate-only-includes">Ultimate-Only Includes Documentation</a>
   */
  fun deprecatedInclude(moduleName: String, resourcePath: String, ultimateOnly: Boolean = false, optional: Boolean = false) {
    xmlIncludes.add(DeprecatedXmlInclude(ContentModuleName(moduleName), resourcePath, ultimateOnly, optional))
    compositionGraph.add(SpecComposition(
      type = CompositionType.DEPRECATED_XML,
      reference = "$moduleName:$resourcePath",
      path = pathStack.toList(),
      sourceLocation = null
    ))
  }

  /**
   * Add a module set without loading overrides.
   */
  fun moduleSet(set: ModuleSet) {
    addModuleSet(set = set, overrides = emptyMap())
  }

  /**
   * Add a module set with loading overrides for specific modules.
   * When overrides are provided, the module set will be inlined in the product XML
   * instead of being referenced via xi:include.
   *
   * Example:
   * ```
   * moduleSet(UltimateModuleSets.commercialIdeBase()) {
   *   overrideAsEmbedded("intellij.rd.platform")
   *   overrideAsEmbedded("intellij.rd.ui")
   *   overrideAsRequired("intellij.some.module")
   * }
   * ```
   */
  inline fun moduleSet(set: ModuleSet, block: ModuleLoadingOverrideBuilder.() -> Unit) {
    addModuleSet(set, ModuleLoadingOverrideBuilder().apply(block).build())
  }

  @PublishedApi
  internal fun addModuleSet(set: ModuleSet, overrides: Map<ContentModuleName, ModuleLoadingRuleValue>) {
    moduleSets.add(ModuleSetWithOverrides(set, overrides))
    compositionGraph.add(SpecComposition(
      type = CompositionType.MODULE_SET_REF,
      reference = set.name,
      path = pathStack.toList(),
      sourceLocation = null
    ))
  }

  /**
   * Add an individual module to additionalModules.
   *
   * @param allowedMissingPluginIds Plugin IDs that are allowed to be missing for auto-added dependencies
   *   discovered from this module (DSL test plugins only).
   */
  fun module(
    name: String,
    loading: ModuleLoadingRuleValue = ModuleLoadingRuleValue.OPTIONAL,
    allowedMissingPluginIds: List<String> = emptyList(),
  ) {
    additionalModules.add(
      ContentModule(
        ContentModuleName(name),
        loading,
        allowedMissingPluginIds = allowedMissingPluginIds.map { PluginId(it) },
      )
    )
    compositionGraph.add(SpecComposition(
      type = CompositionType.DIRECT_MODULE,
      reference = name,
      path = pathStack.toList(),
      sourceLocation = null,
    ))
  }

  /**
   * Add an individual module with EMBEDDED loading to additionalModules.
   *
   * @param allowedMissingPluginIds Plugin IDs that are allowed to be missing for auto-added dependencies
   *   discovered from this module (DSL test plugins only).
   */
  fun embeddedModule(name: String, allowedMissingPluginIds: List<String> = emptyList()) {
    additionalModules.add(
      ContentModule(
        ContentModuleName(name),
        ModuleLoadingRuleValue.EMBEDDED,
        allowedMissingPluginIds = allowedMissingPluginIds.map { PluginId(it) },
      )
    )
    compositionGraph.add(SpecComposition(
      type = CompositionType.DIRECT_MODULE,
      reference = name,
      path = pathStack.toList(),
      sourceLocation = null,
    ))
  }

  /**
   * Add an individual module with REQUIRED loading to additionalModules.
   *
   * @param allowedMissingPluginIds Plugin IDs that are allowed to be missing for auto-added dependencies
   *   discovered from this module (DSL test plugins only).
   */
  fun requiredModule(name: String, allowedMissingPluginIds: List<String> = emptyList()) {
    additionalModules.add(
      ContentModule(
        ContentModuleName(name),
        ModuleLoadingRuleValue.REQUIRED,
        allowedMissingPluginIds = allowedMissingPluginIds.map { PluginId(it) },
      )
    )
    compositionGraph.add(SpecComposition(
      type = CompositionType.DIRECT_MODULE,
      reference = name,
      path = java.util.List.copyOf(pathStack),
      sourceLocation = null,
    ))
  }

  /**
   * Add bundled plugin modules for automatic dependency generation.
   * These are JPS modules that contain META-INF/plugin.xml.
   * The generator will update the `<dependencies>` section in each plugin.xml.
   *
   * @param pluginModules List of JPS module names containing META-INF/plugin.xml
   */
  fun bundledPlugins(pluginModules: List<String>) {
    pluginModules.mapTo(bundledPlugins) { TargetName(it) }
  }

  /**
   * Allow specific modules to be missing during validation.
   * Use for modules provided by plugin layouts rather than module sets.
   *
   * @param modules Module names that are allowed to be missing
   */
  fun allowMissingDependencies(vararg modules: String) {
    for (module in modules) {
      allowedMissingDeps.add(ContentModuleName(module))
    }
  }

  /**
   * Allow specific modules to be missing during validation.
   * Use for modules provided by plugin layouts rather than module sets.
   *
   * @param modules Module names that are allowed to be missing
   */
  fun allowMissingDependencies(modules: List<String>) {
    modules.mapTo(allowedMissingDeps) { ContentModuleName(it) }
  }

  /**
   * Define a test plugin to be generated programmatically.
   * Test plugins provide test framework modules and have plugin.xml in `testResources/META-INF/`.
   *
   * Example:
   * ```
   * testPlugin(
   *   pluginId = "intellij.python.junit5Tests.plugin",
   *   name = "Python Tests Plugin",
   *   pluginXmlPath = "python/junit5Tests/plugin/testResources/META-INF/plugin.xml"
   * ) {
   *   module("intellij.libraries.junit5")
   *   module("intellij.platform.testFramework")
   * }
   * ```
   *
   * @param pluginId The plugin ID (e.g., "intellij.python.junit5Tests.plugin")
   * @param name Human-readable plugin name
   * @param pluginXmlPath Path to the plugin.xml file relative to project root
   * @param additionalBundledPluginTargetNames Additional plugin JPS module target names to treat as bundled for this test plugin's
   *   dependency resolution and auto-add (useful for conditional/runtime plugin inclusion)
   * @param allowedMissingPluginIds Plugin IDs that are allowed to be missing for this test plugin.
   *   If a plugin dependency is inferred but not resolvable, it will be skipped and reported as an error unless listed here.
   *   Use module-level allowedMissingPluginIds for more precise suppression scoped to a single module.
   * @param block DSL block to define the test plugin's content modules
   * @see <a href="../test-plugins.md">Test Plugin Generation Documentation</a>
   */
  inline fun testPlugin(
    pluginId: String,
    name: String,
    pluginXmlPath: String,
    additionalBundledPluginTargetNames: List<String> = emptyList(),
    allowedMissingPluginIds: List<String> = emptyList(),
    block: ProductModulesContentSpecBuilder.() -> Unit,
  ) {
    addTestPlugin(
      pluginId = pluginId,
      name = name,
      pluginXmlPath = pluginXmlPath,
      additionalBundledPluginTargetNames = additionalBundledPluginTargetNames,
      allowedMissingPluginIds = allowedMissingPluginIds,
      spec = ProductModulesContentSpecBuilder().apply(block).build()
    )
  }

  @PublishedApi
  internal fun addTestPlugin(
    pluginId: String,
    name: String,
    pluginXmlPath: String,
    additionalBundledPluginTargetNames: List<String>,
    allowedMissingPluginIds: List<String>,
    spec: ProductModulesContentSpec,
  ) {
    testPlugins.add(
      TestPluginSpec(
        pluginId = PluginId(pluginId),
        name = name,
        pluginXmlPath = pluginXmlPath,
        spec = spec,
        additionalBundledPluginTargetNames = additionalBundledPluginTargetNames.map { TargetName(it) },
        allowedMissingPluginIds = allowedMissingPluginIds.map { PluginId(it) },
      )
    )
  }

  @PublishedApi
  internal fun build(): ProductModulesContentSpec {
    return ProductModulesContentSpec(
      productModuleAliases = java.util.List.copyOf(productModuleAliases),
      vendor = vendor,
      deprecatedXmlIncludes = java.util.List.copyOf(xmlIncludes),
      moduleSets = java.util.List.copyOf(moduleSets),
      additionalModules = java.util.List.copyOf(additionalModules),
      bundledPlugins = java.util.List.copyOf(bundledPlugins),
      allowedMissingDependencies = java.util.Set.copyOf(allowedMissingDeps),
      compositionGraph = java.util.List.copyOf(compositionGraph),
      metadata = metadata,
      testPlugins = java.util.List.copyOf(testPlugins),
    )
  }
}

/**
 * Creates a ProductModulesContentSpec using DSL syntax.
 *
 * Example:
 * ```
 * override fun getProductContentModules(): ProductModulesContentSpec {
 *   return productModules {
 *     // XML includes (optional)
 *     include("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
 *     include("intellij.gateway", "META-INF/Gateway.xml")
 *
 *     // Module sets
 *     moduleSet(UltimateModuleSets.ssh())
 *     moduleSet(CommunityModuleSets.vcs())
 *
 *     // Individual modules
 *     embeddedModule("com.example.additional")
 *
 *     // Exclusions
 *     exclude("unwanted.module")
 *   }
 * }
 * ```
 */
inline fun productModules(block: ProductModulesContentSpecBuilder.() -> Unit): ProductModulesContentSpec {
  return ProductModulesContentSpecBuilder().apply(block).build()
}

/**
 * Specification for a test plugin that will have its plugin.xml generated programmatically.
 * Test plugins are used for running tests and provide test framework modules.
 *
 * @param pluginId The plugin XML ID (e.g., "intellij.python.junit5Tests.plugin")
 * @param name Human-readable plugin name for the `<name>` tag
 * @param pluginXmlPath Path to the plugin.xml file relative to project root
 * @param spec The content specification using the same DSL as products
 * @param additionalBundledPluginTargetNames Extra plugin JPS module target names to treat as bundled for this test plugin's
 *   dependency resolution and auto-add
 * @param allowedMissingPluginIds Plugin IDs that are allowed to be missing for this test plugin
 */
@Serializable
data class TestPluginSpec(
  val pluginId: PluginId,
  @JvmField val name: String,
  @JvmField val pluginXmlPath: String,
  @JvmField val spec: ProductModulesContentSpec,
  @JvmField val additionalBundledPluginTargetNames: List<TargetName> = emptyList(),
  @JvmField val allowedMissingPluginIds: List<PluginId> = emptyList(),
)
