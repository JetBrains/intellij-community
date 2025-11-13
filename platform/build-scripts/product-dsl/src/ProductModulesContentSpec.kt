// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule
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
 * @param moduleName The name of the module containing the resource (e.g., "intellij.platform.resources")
 * @param resourcePath The path to the resource within the module (e.g., "META-INF/PlatformLangPlugin.xml")
 * @param ultimateOnly If true, this include is only processed in Ultimate builds (skipped in Community builds)
 * @param optional If true, this include is always generated with xi:fallback and never inlined (safe for files that may not exist)
 */
@Serializable
data class DeprecatedXmlInclude(
  @JvmField val moduleName: String,
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
   * Product module aliases for `<module value="..."/>` declarations (e.g., "com.jetbrains.gateway", "com.intellij.modules.idea").
   * These will be generated as the first elements in the programmatic content.
   * Multiple aliases can be specified by calling alias() multiple times in the DSL.
   */
  @JvmField val productModuleAliases: List<String>,

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
)

/**
 * DSL builder for creating ProductModulesContentSpec with reduced boilerplate.
 */
@ProductDslMarker
class ProductModulesContentSpecBuilder @PublishedApi internal constructor() {
  private val productModuleAliases = mutableListOf<String>()
  private var vendor: String? = null
  private val xmlIncludes = mutableListOf<DeprecatedXmlInclude>()
  private val moduleSets = mutableListOf<ModuleSetWithOverrides>()
  private val additionalModules = mutableListOf<ContentModule>()

  // Composition tracking
  private val compositionGraph = mutableListOf<SpecComposition>()
  private val pathStack = mutableListOf<String>() // Current path for nested compositions

  // Metadata for this spec
  internal var metadata: SpecMetadata? = null

  /**
   * Add a product module alias for `<module value="..."/>` declaration.
   * Can be called multiple times to add multiple aliases.
   * Example: alias("com.jetbrains.gateway")
   * Example: alias("com.intellij.modules.idea")
   */
  fun alias(value: String) {
    productModuleAliases.add(value)
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
   * @param moduleName The name of the module containing the resource
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
    xmlIncludes.add(DeprecatedXmlInclude(moduleName, resourcePath, ultimateOnly, optional))
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
  internal fun addModuleSet(set: ModuleSet, overrides: Map<String, ModuleLoadingRule>) {
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
   */
  fun module(name: String, loading: ModuleLoadingRule? = null) {
    additionalModules.add(ContentModule(name, loading))
    compositionGraph.add(SpecComposition(
      type = CompositionType.DIRECT_MODULE,
      reference = name,
      path = pathStack.toList(),
      sourceLocation = null
    ))
  }

  /**
   * Add an individual module with EMBEDDED loading to additionalModules.
   */
  fun embeddedModule(name: String) {
    additionalModules.add(ContentModule(name, ModuleLoadingRule.EMBEDDED))
    compositionGraph.add(SpecComposition(
      type = CompositionType.DIRECT_MODULE,
      reference = name,
      path = pathStack.toList(),
      sourceLocation = null
    ))
  }

  /**
   * Add an individual module with REQUIRED loading to additionalModules.
   */
  fun requiredModule(name: String) {
    additionalModules.add(ContentModule(name, ModuleLoadingRule.REQUIRED))
    compositionGraph.add(SpecComposition(
      type = CompositionType.DIRECT_MODULE,
      reference = name,
      path = java.util.List.copyOf(pathStack),
      sourceLocation = null,
    ))
  }

  @PublishedApi
  internal fun build(): ProductModulesContentSpec {
    return ProductModulesContentSpec(
      productModuleAliases = java.util.List.copyOf(productModuleAliases),
      vendor = vendor,
      deprecatedXmlIncludes = java.util.List.copyOf(xmlIncludes),
      moduleSets = java.util.List.copyOf(moduleSets),
      additionalModules = java.util.List.copyOf(additionalModules),
      compositionGraph = java.util.List.copyOf(compositionGraph),
      metadata = metadata,
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