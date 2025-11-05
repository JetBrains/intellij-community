// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule

/**
 * Represents an XML include directive that references a resource within a module.
 *
 * @param moduleName The name of the module containing the resource (e.g., "intellij.platform.resources")
 * @param resourcePath The path to the resource within the module (e.g., "META-INF/PlatformLangPlugin.xml")
 * @param ultimateOnly If true, this include is only processed in Ultimate builds (skipped in Community builds)
 */
data class DeprecatedXmlInclude(
  @JvmField val moduleName: String,
  @JvmField val resourcePath: String,
  @JvmField val ultimateOnly: Boolean = false,
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
class ProductModulesContentSpec(
  /**
   * Product module aliases for `<module value="..."/>` declarations (e.g., "com.jetbrains.gateway", "com.intellij.modules.idea").
   * These will be generated as the first elements in the programmatic content.
   * Multiple aliases can be specified by calling alias() multiple times in the DSL.
   */
  @JvmField val productModuleAliases: List<String>,

  /**
   * XML includes to add (xi:include directives).
   * These will be generated before module content blocks.
   * Each include specifies a module name and resource path within that module.
   */
  @JvmField val deprecatedXmlIncludes: List<DeprecatedXmlInclude>,

  /**
   * Module sets to include. Each set contains a named collection of modules.
   * Module sets are processed in order and can overlap (duplicates are handled).
   */
  @JvmField val moduleSets: List<ModuleSet>,

  /**
   * Additional individual modules to include beyond those from module sets.
   * This allows fine-grained control to add specific modules not in any set.
   */
  @JvmField val additionalModules: List<ContentModule>,

  /**
   * Module names to exclude from the final set (after resolving all module sets and additions).
   * This is useful to remove specific modules from included module sets.
   */
  @JvmField val excludedModules: Set<String>,

  /**
   * Loading attribute overrides for specific modules.
   * Map of module name to loading mode (e.g., [ModuleLoadingRule.EMBEDDED]).
   * This allows changing the loading mode of modules from module sets.
   */
  @JvmField val moduleLoadingOverrides: Map<String, ModuleLoadingRule>,
)

/**
 * DSL builder for creating ProductModulesContentSpec with reduced boilerplate.
 */
class ProductModulesContentSpecBuilder @PublishedApi internal constructor() {
  private val productModuleAliases = mutableListOf<String>()
  private val xmlIncludes = mutableListOf<DeprecatedXmlInclude>()
  private val moduleSets = mutableListOf<ModuleSet>()
  private val additionalModules = mutableListOf<ContentModule>()
  private val excludedModules = mutableSetOf<String>()
  private val loadingOverrides = mutableMapOf<String, ModuleLoadingRule>()

  /**
   * Add a product module alias for `<module value="..."/>` declaration.
   * Can be called multiple times to add multiple aliases.
   * Example: alias("com.jetbrains.gateway")
   * Example: alias("com.intellij.modules.idea")
   */
  fun alias(value: String) {
    productModuleAliases.add(value)
  }

  /**
   * Add an XML include (xi:include directive) by specifying module name and resource path.
   * Example: deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
   *
   * For Ultimate-only includes that should be conditionally processed:
   * Example: deprecatedInclude("intellij.platform.extended.community.impl", "META-INF/community-extensions.xml", ultimateOnly = true)
   *
   * @param moduleName The name of the module containing the resource
   * @param resourcePath The path to the resource within the module
   * @param ultimateOnly If true, this include is only processed in Ultimate builds.
   *   - When inlining: Skipped in Community builds
   *   - When NOT inlining: Generates xi:include with xi:fallback wrapper for graceful handling
   *
   * @see <a href="programmatic-content.md#ultimate-only-includes">Ultimate-Only Includes Documentation</a>
   */
  fun deprecatedInclude(moduleName: String, resourcePath: String, ultimateOnly: Boolean = false) {
    xmlIncludes.add(DeprecatedXmlInclude(moduleName, resourcePath, ultimateOnly))
  }

  /**
   * Add a module set.
   */
  fun moduleSet(set: ModuleSet) {
    moduleSets.add(set)
  }

  /**
   * Add an individual module to additionalModules.
   */
  fun module(name: String, loading: ModuleLoadingRule? = null) {
    additionalModules.add(ContentModule(name, loading))
  }

  /**
   * Add an individual module with EMBEDDED loading to additionalModules.
   */
  fun embeddedModule(name: String) {
    additionalModules.add(ContentModule(name, ModuleLoadingRule.EMBEDDED))
  }

  /**
   * Add an individual module with REQUIRED loading to additionalModules.
   */
  fun requiredModule(name: String) {
    additionalModules.add(ContentModule(name, ModuleLoadingRule.REQUIRED))
  }

  /**
   * Exclude a module from the final set.
   */
  fun exclude(moduleName: String) {
    excludedModules.add(moduleName)
  }

  /**
   * Override the loading mode for a specific module.
   */
  fun override(moduleName: String, loading: ModuleLoadingRule) {
    loadingOverrides.put(moduleName, loading)
  }

  @PublishedApi
  internal fun build(): ProductModulesContentSpec {
    return ProductModulesContentSpec(
      productModuleAliases = java.util.List.copyOf(productModuleAliases),
      deprecatedXmlIncludes = java.util.List.copyOf(xmlIncludes),
      moduleSets = java.util.List.copyOf(moduleSets),
      additionalModules = java.util.List.copyOf(additionalModules),
      excludedModules = java.util.Set.copyOf(excludedModules),
      moduleLoadingOverrides = java.util.Map.copyOf(loadingOverrides),
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
 *     // Exclusions and overrides
 *     exclude("unwanted.module")
 *     override("some.module", ModuleLoadingRule.OPTIONAL)
 *   }
 * }
 * ```
 */
inline fun productModules(block: ProductModulesContentSpecBuilder.() -> Unit): ProductModulesContentSpec {
  return ProductModulesContentSpecBuilder().apply(block).build()
}