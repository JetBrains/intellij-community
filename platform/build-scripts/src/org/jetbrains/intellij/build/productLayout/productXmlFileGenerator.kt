// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.dev.createProductProperties
import org.jetbrains.intellij.build.impl.jpsModuleOutputProvider
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import org.jetbrains.jps.model.serialization.JpsSerializationManager
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
 * Converts a DiscoveredProduct to ProductSpec for JSON output.
 * Extension function that has access to ProductProperties types.
 * Includes full ProductModulesContentSpec for complete DSL representation.
 */
internal fun DiscoveredProduct.toProductSpec(projectRoot: Path): ProductSpec {
  // For test products (properties = null), use "test-product" as source file
  val sourceFile = if (properties == null) {
    "test-product"
  }
  else {
    getProductPropertiesSourceFile(properties.javaClass, projectRoot)
  }

  return ProductSpec(
    name = name,
    className = config.className,
    sourceFile = sourceFile,
    pluginXmlPath = pluginXmlPath,
    contentSpec = spec,  // Pass full ProductModulesContentSpec for complete DSL serialization
    buildModules = config.modules
  )
}

/**
 * Discovers all products from dev-build.json registry (internal representation).
 * Returns DiscoveredProduct instances for internal use by XML generator.
 *
 * @param projectRoot The project root path
 * @return List of discovered products with all metadata
 */
private suspend fun discoverAllProductsInternal(projectRoot: Path): List<DiscoveredProduct> {
  val jsonContent = Files.readString(projectRoot.resolve(PRODUCT_REGISTRY_PATH))
  val productToConfiguration = Json.decodeFromString<ProductConfigurationRegistry>(jsonContent).products

  val project = JpsSerializationManager.getInstance().loadProject(
    projectRoot.toString(),
    mapOf("MAVEN_REPOSITORY" to JpsMavenSettings.getMavenRepositoryPath()),
    false
  )
  val moduleOutputProvider = jpsModuleOutputProvider(project.modules)

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
 * Discovers all products from dev-build.json registry and converts to ProductSpec for JSON output.
 * Public function that can be called from other modules (like ultimate buildScripts).
 * Test products should be passed separately via runModuleSetMain's testProducts parameter.
 *
 * @param projectRoot The project root path
 * @return List of products as ProductSpec (simple data class with no internal dependencies)
 */
suspend fun discoverAllProductsForJson(projectRoot: Path): Sequence<ProductSpec> {
  val regularProducts = discoverAllProductsInternal(projectRoot)
  return regularProducts.asSequence().map { it.toProductSpec(projectRoot) }
}

/**
 * Discovers all products for validation purposes.
 * Returns list of (productName, ProductModulesContentSpec) pairs.
 *
 * @param projectRoot The project root path
 * @return List of product name and spec pairs for validation
 */
suspend fun discoverAllProductsForValidation(projectRoot: Path): List<Pair<String, ProductModulesContentSpec?>> {
  val discovered = discoverAllProductsInternal(projectRoot)
  return discovered.map { it.name to it.spec }
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
): ProductGenerationResult {
  val regularProducts = discoverAllProductsInternal(projectRoot)

  // Convert test product specs to DiscoveredProduct instances
  val testProducts = testProductSpecs.mapNotNull { (name, spec) ->
    val xmlPath = "ultimate/platform-ultimate/testResources/META-INF/${name}Plugin.xml"
    val xmlFile = projectRoot.resolve(xmlPath)
    if (!Files.exists(xmlFile)) return@mapNotNull null

    DiscoveredProduct(
      name = name,
      config = ProductConfiguration(
        className = "test-product",
        modules = emptyList(),
        pluginXmlPath = xmlPath
      ),
      properties = null,
      spec = spec,
      pluginXmlPath = xmlPath
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
    val moduleOutputProvider = jpsModuleOutputProvider(
      JpsSerializationManager.getInstance()
        .loadProject(projectRoot.toString(), mapOf("MAVEN_REPOSITORY" to JpsMavenSettings.getMavenRepositoryPath()), false)
        .modules
    )

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