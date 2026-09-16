// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.productLayout.discovery.PRODUCT_REGISTRY_PATH
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfigurationRegistry
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * The product token of the `ProductProperties` class [className] in the dev-distribution tables.
 *
 * The token is the first key of `build/dev-build.json`, in file order, whose product has the class. The file names one
 * class under several keys, such as `community` and `Idea`, and marks no key as the alias, so the file order decides.
 * `plugin-model-tool` and the packaging tests both resolve the token here, so the two tables name one product by one
 * token. The platform prefix cannot serve as the token, because every language server shares one prefix.
 *
 * A class no key names is an error, because no tool run states rows for that product.
 */
@Internal
fun devDistProductToken(registry: ProductConfigurationRegistry, className: String): String {
  return registry.products.entries.firstOrNull { it.value.className == className }?.key
         ?: error("No product of `$PRODUCT_REGISTRY_PATH` has the class `$className`, so no token names this product in the dev-distribution tables")
}

/** The product token of [productProperties], resolved from the `build/dev-build.json` under [projectHome]. */
@Internal
fun devDistProductToken(projectHome: Path, productProperties: ProductProperties): String {
  return devDistProductToken(readDevDistProductRegistry(projectHome), productProperties.javaClass.name)
}

/** Reads `build/dev-build.json` under [projectHome], with the products in file order. */
@Internal
fun readDevDistProductRegistry(projectHome: Path): ProductConfigurationRegistry {
  return Json.decodeFromString<ProductConfigurationRegistry>(projectHome.resolve(PRODUCT_REGISTRY_PATH).readText())
}
