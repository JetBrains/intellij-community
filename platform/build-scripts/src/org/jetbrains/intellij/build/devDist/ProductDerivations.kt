// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import com.intellij.platform.distributionContent.DevDistPlatformJars
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.productLayout.discoverAllProducts
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.JpsProject
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * The products of `build/dev-build.json` under one project root, and the derivations every reader of them shares.
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
 * The key of one derivation: the project root and the JPS project the provider reads.
 *
 * The project is compared by identity. Two providers over one loaded project share a derivation, and a test that loads
 * the same root twice gets two.
 */
private class DerivationKey(@JvmField val projectRoot: Path, @JvmField val project: JpsProject?) {
  override fun equals(other: Any?): Boolean {
    return other is DerivationKey && projectRoot == other.projectRoot && project === other.project
  }

  override fun hashCode(): Int = 31 * projectRoot.hashCode() + System.identityHashCode(project)
}

private val derivations = ConcurrentHashMap<DerivationKey, Lazy<ProductDerivation>>()

/**
 * The one [ProductDerivation] of [projectRoot] in this process.
 *
 * The first caller derives, and every caller that arrives while it runs waits for the same result. The suite runs
 * its validations beside each other, and three of them read this. A failed derivation is not kept, so the next caller
 * derives again.
 *
 * The product discovery loads every `ProductProperties` class from compiled build modules, which [outputProvider]
 * names. The derivation then reads the product declarations and the source descriptors.
 */
@ApiStatus.Internal
fun productDerivation(projectRoot: Path, outputProvider: ModuleOutputProvider): ProductDerivation {
  val key = DerivationKey(
    projectRoot = projectRoot.toAbsolutePath().normalize(),
    project = outputProvider.getAllModules().firstOrNull()?.project,
  )
  return derivations.computeIfAbsent(key) {
    lazy {
      spanBuilder("derive products").setAttribute("projectRoot", key.projectRoot.toString()).use { span ->
        val products = discoverAllProducts(projectRoot = key.projectRoot, outputProvider = outputProvider)
        span.setAttribute("productCount", products.size.toLong())
        ProductDerivation(
          products = products,
          platformJars = deriveDevDistPlatformJars(products = products, outputProvider = outputProvider),
          outputProvider = outputProvider,
        )
      }
    }
  }.value
}
