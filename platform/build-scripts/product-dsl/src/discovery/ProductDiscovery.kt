// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.discovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Path

/**
 * Path to the product registry JSON file.
 */
const val PRODUCT_REGISTRY_PATH: String = "build/dev-build.json"

/**
 * Product registry containing all product configurations from dev-build.json.
 */
@Serializable
data class ProductConfigurationRegistry(@JvmField val products: Map<String, ProductConfiguration>)

/**
 * Product configuration from dev-build.json.
 */
@Serializable
data class ProductConfiguration(
  @JvmField val modules: List<String>,
  @JvmField @SerialName("class") val className: String,
  @JvmField val pluginXmlPath: String? = null,
)

/**
 * Represents a discovered product with all its metadata.
 * Used by both XML and JSON generators to avoid code duplication.
 * Test products have properties = null (they don't have ProductProperties classes).
 *
 * Note: The `properties` field uses type `Any?` instead of `ProductProperties?` to avoid
 * depending on the full build-scripts module (would create circular dependency).
 * In practice, it holds ProductProperties instances or null for test products.
 */
data class DiscoveredProduct(
  @JvmField val name: String,
  @JvmField val config: ProductConfiguration,
  @JvmField val properties: Any?, // ProductProperties or null
  @JvmField val spec: org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec?,
  @JvmField val pluginXmlPath: String?,
)

/**
 * Map of class FQN to actual file name for cases where class name != file name.
 * Example: DotnetExternalProductProperties class is in ReSharperExternalProductProperties.kt file.
 */
private val CLASS_TO_FILE_NAME_OVERRIDES = mapOf(
  "com.jetbrains.rider.build.product.DotnetExternalProductProperties" to "ReSharperExternalProductProperties.kt",
  "org.jetbrains.intellij.build.AndroidStudioWithMarketplaceProperties" to "UltimateAwareIdeaCommunityProperties.kt",
)

/**
 * Finds the actual source file for a ProductProperties class by searching JPS module source roots.
 * This replaces hardcoded path mapping with actual file system lookup.
 *
 * @param buildModules List of build module names from dev-build.json (e.g., ["intellij.goland.build"])
 * @param productPropertiesClass The ProductProperties class to find
 * @param outputProvider Provider for accessing JPS modules
 * @param projectRoot Project root path for making paths relative
 * @return Relative path to the source file
 */
fun findProductPropertiesSourceFile(
  buildModules: List<String>,
  productPropertiesClass: Class<*>,
  outputProvider: ModuleOutputProvider,
  projectRoot: Path,
): String {
  val className = productPropertiesClass.name

  // Handle special cases where class name != file name
  val fileName = CLASS_TO_FILE_NAME_OVERRIDES[className] ?: "${className.substringAfterLast('.')}.kt"
  val packagePath = className.substringBeforeLast('.').replace('.', '/')
  val relativePath = "$packagePath/$fileName"

  // Search each build module's source roots
  for (buildModuleName in buildModules) {
    val jpsModule = outputProvider.findModule(buildModuleName) ?: continue

    // Search production source roots (not test roots)
    val sourceFile = jpsModule.sourceRoots
      .asSequence()
      .filter { it.rootType == JavaSourceRootType.SOURCE }
      .firstNotNullOfOrNull { sourceRoot ->
        JpsJavaExtensionService.getInstance().findSourceFile(sourceRoot, relativePath)
      }

    if (sourceFile != null) {
      return projectRoot.relativize(sourceFile).toString()
    }
  }

  throw IllegalStateException("Cannot find source file for $productPropertiesClass (searched for $relativePath in modules: $buildModules)")
}
