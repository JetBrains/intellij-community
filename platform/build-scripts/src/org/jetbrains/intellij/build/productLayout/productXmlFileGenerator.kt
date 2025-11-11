// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.dev.createProductProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents a discovered product with all its metadata.
 * Used by both XML and JSON generators to avoid code duplication.
 * Test products have properties = null (they don't have ProductProperties classes).
 */
internal data class DiscoveredProduct(
  @JvmField val name: String,
  @JvmField val config: ProductConfiguration,
  @JvmField val properties: ProductProperties?,
  @JvmField val spec: ProductModulesContentSpec?,
  @JvmField val pluginXmlPath: String?,
)

/**
 * Map of class FQN to actual file name for cases where class name != file name.
 * Example: DotnetExternalProductProperties class is in ReSharperExternalProductProperties.kt file.
 */
private val CLASS_TO_FILE_NAME_OVERRIDES = mapOf(
  "com.jetbrains.rider.build.product.DotnetExternalProductProperties" to "ReSharperExternalProductProperties.kt"
)

/**
 * Finds the actual source file for a ProductProperties class by searching JPS module source roots.
 * This replaces hardcoded path mapping with actual file system lookup.
 *
 * @param buildModules List of build module names from dev-build.json (e.g., ["intellij.goland.build"])
 * @param productPropertiesClass The ProductProperties class to find
 * @param moduleOutputProvider Provider for accessing JPS modules
 * @param projectRoot Project root path for making paths relative
 * @return Relative path to the source file
 */
fun findProductPropertiesSourceFile(
  buildModules: List<String>,
  productPropertiesClass: Class<*>,
  moduleOutputProvider: ModuleOutputProvider,
  projectRoot: Path
): String {
  val className = productPropertiesClass.name

  // Handle special cases where class name != file name
  val fileName = CLASS_TO_FILE_NAME_OVERRIDES[className] ?: "${className.substringAfterLast('.')}.kt"
  val packagePath = className.substringBeforeLast('.').replace('.', '/')
  val relativePath = "$packagePath/$fileName"

  // Search each build module's source roots
  for (buildModuleName in buildModules) {
    val jpsModule = moduleOutputProvider.findModule(buildModuleName) ?: continue

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

/**
 * Discovers all products from dev-build.json registry (internal representation).
 * Returns DiscoveredProduct instances for internal use by XML generator.
 *
 * @param projectRoot The project root path
 * @return List of discovered products with all metadata
 */
internal suspend fun discoverAllProducts(projectRoot: Path, moduleOutputProvider: ModuleOutputProvider): List<DiscoveredProduct> {
  val jsonContent = Files.readString(projectRoot.resolve(PRODUCT_REGISTRY_PATH))
  val productToConfiguration = Json.decodeFromString<ProductConfigurationRegistry>(jsonContent).products
  val products = mutableListOf<DiscoveredProduct>()
  for ((productName, productConfig) in productToConfiguration) {
    val productProperties = createProductProperties(
      productConfiguration = productConfig,
      moduleOutputProvider = moduleOutputProvider,
      projectDir = projectRoot,
      platformPrefix = null
    )
    val spec = productProperties.getProductContentDescriptor()
    products.add(
      DiscoveredProduct(
        name = productName,
        config = productConfig,
        properties = productProperties,
        spec = spec,
        pluginXmlPath = productConfig.pluginXmlPath
      )
    )
  }

  return products
}


/**
 * Discovers all products for validation purposes.
 * Returns list of (productName, ProductModulesContentSpec) pairs.
 *
 * @param projectRoot The project root path
 * @return List of product name and spec pairs for validation
 */
suspend fun discoverAllProductsForValidation(projectRoot: Path, moduleOutputProvider: ModuleOutputProvider): List<Pair<String, ProductModulesContentSpec?>> {
  return discoverAllProducts(projectRoot, moduleOutputProvider).map { it.name to it.spec }
}

/**
 * Generates product XMLs for all products using programmatic content.
 * Discovers products from dev-build.json and accepts test products as parameter, then generates complete plugin.xml files.
 *
 * @param projectRoot The project root path
 * @param testProductSpecs Test product specifications (name to ProductModulesContentSpec pairs)
 * @return Result containing generation statistics
 */
suspend fun generateAllProductXmlFiles(
  projectRoot: Path,
  testProductSpecs: List<Pair<String, ProductModulesContentSpec>> = emptyList(),
  moduleOutputProvider: ModuleOutputProvider,
): ProductGenerationResult {
  val regularProducts = discoverAllProducts(projectRoot, moduleOutputProvider)

  // Convert test product specs to DiscoveredProduct instances
  val testProducts = testProductSpecs.mapNotNull { (name, spec) ->
    val xmlPath = "ultimate/platform-ultimate/testResources/META-INF/${name}Plugin.xml"
    val xmlFile = projectRoot.resolve(xmlPath)
    if (Files.notExists(xmlFile)) {
      return@mapNotNull null
    }

    DiscoveredProduct(
      name = name,
      config = ProductConfiguration(
        className = "test-product",
        modules = emptyList(),
        pluginXmlPath = xmlPath,
      ),
      properties = null,
      spec = spec,
      pluginXmlPath = xmlPath,
    )
  }

  val products = regularProducts + testProducts

  // Detect if this is an Ultimate build (projectRoot != communityRoot)
  val isUltimateBuild = projectRoot != BuildPaths.COMMUNITY_ROOT.communityRoot

  val productResults = products.mapNotNull { discovered ->
    // Skip products without pluginXmlPath or spec configured
    val pluginXmlRelativePath = discovered.pluginXmlPath ?: return@mapNotNull null
    val spec = discovered.spec ?: return@mapNotNull null

    val pluginXmlPath = projectRoot.resolve(pluginXmlRelativePath)
    generateProductXml(
      pluginXmlPath = pluginXmlPath,
      spec = spec,
      productName = discovered.name,
      moduleOutputProvider = moduleOutputProvider,
      productPropertiesClass = discovered.properties?.javaClass?.name ?: "test-product",
      projectRoot = projectRoot,
      isUltimateBuild = isUltimateBuild,
    )
  }

  return ProductGenerationResult(productResults)
}