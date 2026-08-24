// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.ProductNode
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.model.error.ImplicitEmbeddedContentModuleError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.traversal.analyzeImplicitEmbeddedContentModules

/**
 * Enforces that an embedded module with `includeDependencies=true` does not silently
 * pull content modules into the product via transitive JPS runtime deps — every such
 * content module must be explicitly declared by the product (content spec, module set,
 * or bundled plugin content).
 *
 * Purpose:
 * At packaging time, `computeEmbeddedModuleDependencies` walks the production-runtime
 * JPS closure of every embedded module declared with `includeDependencies=true` and
 * packs the result into the embedded module's jar. Any content module reached along the
 * way is silently bundled, making the jar contents vary across products and bypassing
 * the plugin model as the single source of truth for what a product ships.
 *
 * This validator fails early, in product-dsl, by doing a BFS from each such root and
 * reporting a violation whenever a descriptor-backed content module not declared by the
 * product is reached. Each product must either:
 *   1. list the content module explicitly (via its content spec / module set), or
 *   2. break the JPS chain so the module is no longer reachable.
 *
 * Only content modules (descriptor-backed) are flagged — plain JPS targets continue to
 * flow transparently. A violation is suppressed when the target is listed in the
 * product's [ProductModulesContentSpec.allowedMissingDependencies] — matching the
 * existing packaging-time runtime check (`validateImplicitPlatformModule`) so this
 * validator is a strict superset of it without widening the allowlist semantics.
 *
 * Non-embedded (regular) DSL modules are NOT roots here: they have their own plugin
 * classloader and can depend on content modules of other plugins via the runtime
 * plugin model. Legacy platform-layout implicit pulls are still covered by the
 * runtime `validateImplicitPlatformModule` check at packaging time.
 *
 * Inputs: plugin graph (targets + JPS deps + descriptor flags), discovered products.
 * Output: [ImplicitEmbeddedContentModuleError].
 */
internal object ImplicitEmbeddedContentModuleValidator : PipelineNode {
  override val id get() = NodeIds.IMPLICIT_EMBEDDED_CONTENT_MODULE_VALIDATION

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val pluginGraph = model.pluginGraph
    val productsByName = model.discovery.products.associateBy { it.name }
    pluginGraph.forEachProductParallel { product ->
      val discovered = productsByName[pluginGraph.query { product.name() }] ?: return@forEachProductParallel
      validateProduct(product = product, discovered = discovered, pluginGraph = pluginGraph)?.let(ctx::emitError)
    }
  }
}

private fun validateProduct(
  product: ProductNode,
  discovered: DiscoveredProduct,
  pluginGraph: PluginGraph,
): ImplicitEmbeddedContentModuleError? {
  val spec = discovered.spec ?: return null
  val analysis = pluginGraph.query { analyzeImplicitEmbeddedContentModules(product, spec) }
  if (analysis.missingModules.isEmpty()) return null
  return ImplicitEmbeddedContentModuleError(
    context = pluginGraph.query { product.name() },
    missingModules = analysis.missingModules,
    chains = analysis.chains,
  )
}
