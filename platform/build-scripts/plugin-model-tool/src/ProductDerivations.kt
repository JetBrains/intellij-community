// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.pluginModelTool

import com.intellij.platform.distributionContent.DevDistPlatformJars
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * The products of `build/dev-build.json` under one project root, and the derivations of them.
 *
 * [deriveProducts] makes an instance. The owner shares that one instance with its readers.
 *
 * [products] is the discovery, [platformJars] the source-derived platform rows of every product. [pluginJars] derives
 * the plugin population once per extra population, because two readers state two extra sets and a plugin's packing
 * does not depend on which one asked.
 */
@ApiStatus.Internal
class ProductDerivation internal constructor(
  @JvmField val products: List<DiscoveredProduct>,
  @JvmField val platformJars: DevDistPlatformJars,
  private val outputProvider: ModuleOutputProvider,
) {
  /** The products that have a `ProductProperties` class, in discovery order. */
  @JvmField
  val properties: List<ProductProperties> = products.mapNotNull { it.properties as? ProductProperties }

  private val pluginJarsByExtraPopulation = ConcurrentHashMap<Set<String>, Lazy<DerivedPluginJars>>()

  /** The derivation of every plugin of the population; see [derivePluginJars]. Derived once per [extraPopulation]. */
  fun pluginJars(extraPopulation: Set<String>): DerivedPluginJars {
    val key = java.util.Set.copyOf(extraPopulation)
    return pluginJarsByExtraPopulation.computeIfAbsent(key) {
      lazy {
        derivePluginJars(products = properties, extraPopulation = key, platformJars = platformJars, outputProvider = outputProvider)
      }
    }.value
  }
}

/**
 * A new [ProductDerivation] of [projectRoot]. The caller owns it and shares it with its readers.
 *
 * The product discovery loads every `ProductProperties` class from compiled build modules, which [outputProvider]
 * names. The derivation then reads the product declarations and the source descriptors.
 */
@ApiStatus.Internal
fun deriveProducts(projectRoot: Path, outputProvider: ModuleOutputProvider): ProductDerivation {
  val normalizedRoot = projectRoot.toAbsolutePath().normalize()
  return spanBuilder("derive products").setAttribute("projectRoot", normalizedRoot.toString()).use { span ->
    val products = discoverAllProducts(projectRoot = normalizedRoot, outputProvider = outputProvider)
    span.setAttribute("productCount", products.size.toLong())
    ProductDerivation(
      products = products,
      platformJars = deriveDevDistPlatformJars(products = products, outputProvider = outputProvider),
      outputProvider = outputProvider,
    )
  }
}
