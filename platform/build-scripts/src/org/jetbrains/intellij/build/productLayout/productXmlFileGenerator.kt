// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.dev.createProductProperties
import java.nio.file.Files
import java.nio.file.Path

/**
 * Discovers all products from dev-build.json registry with their ProductProperties instances.
 * This is the only function in build-scripts that depends on ProductProperties.
 * All other product-related generation logic has been moved to product-dsl module.
 *
 * @param projectRoot The project root path
 * @param moduleOutputProvider Module output provider for creating ProductProperties
 * @return List of discovered products with ProductProperties instances
 */
suspend fun discoverAllProducts(projectRoot: Path, moduleOutputProvider: ModuleOutputProvider): List<DiscoveredProduct> {
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
        properties = productProperties, // ProductProperties instance
        spec = spec,
        pluginXmlPath = productConfig.pluginXmlPath
      )
    )
  }

  return products
}

/**
 * Discovers all products for validation purposes.
 * Convenience wrapper that calls discoverAllProducts() and extracts validation data.
 *
 * @param projectRoot The project root path
 * @param moduleOutputProvider Module output provider
 * @return List of product name and spec pairs for validation
 */
suspend fun discoverAllProductsForValidation(projectRoot: Path, moduleOutputProvider: ModuleOutputProvider): List<Pair<String, ProductModulesContentSpec?>> {
  return extractProductsForValidation(discoverAllProducts(projectRoot, moduleOutputProvider))
}

/**
 * Generates product XMLs for all products using programmatic content.
 * Convenience wrapper that discovers products then delegates to product-dsl generation logic.
 *
 * @param projectRoot The project root path
 * @param testProductSpecs Test product specifications (name to ProductModulesContentSpec pairs)
 * @param moduleOutputProvider Module output provider
 * @return Result containing generation statistics
 */
suspend fun generateAllProductXmlFiles(
  projectRoot: Path,
  testProductSpecs: List<Pair<String, ProductModulesContentSpec>> = emptyList(),
  moduleOutputProvider: ModuleOutputProvider,
): ProductGenerationResult {
  val discoveredProducts = discoverAllProducts(projectRoot, moduleOutputProvider)
  return generateAllProductXmlFiles(
    discoveredProducts = discoveredProducts,
    testProductSpecs = testProductSpecs,
    projectRoot = projectRoot,
    moduleOutputProvider = moduleOutputProvider
  )
}