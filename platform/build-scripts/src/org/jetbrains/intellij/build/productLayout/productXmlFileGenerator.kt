// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.dev.createProductProperties
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.discovery.PRODUCT_REGISTRY_PATH
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfigurationRegistry
import java.nio.file.Files
import java.nio.file.Path

/**
 * Discovers all products from dev-build.json registry with their ProductProperties instances.
 * This is the only function in build-scripts that depends on ProductProperties.
 * All other product-related generation logic has been moved to product-dsl module.
 *
 * @param projectRoot The project root path
 * @param outputProvider Module output provider for creating ProductProperties
 * @return List of discovered products with ProductProperties instances
 */
suspend fun discoverAllProducts(projectRoot: Path, outputProvider: ModuleOutputProvider): List<DiscoveredProduct> {
  val jsonContent = Files.readString(projectRoot.resolve(PRODUCT_REGISTRY_PATH))
  val productToConfiguration = Json.decodeFromString<ProductConfigurationRegistry>(jsonContent).products

  return coroutineScope {
    productToConfiguration.map { (productName, productConfig) ->
      async {
        val productProperties = createProductProperties(
          productConfiguration = productConfig,
          outputProvider = outputProvider,
          projectDir = projectRoot,
          platformPrefix = null,
        )
        DiscoveredProduct(
          name = productName,
          config = productConfig,
          properties = productProperties,
          spec = productProperties.getProductContentDescriptor(),
          pluginXmlPath = productConfig.pluginXmlPath,
        )
      }
    }.awaitAll()
  }
}