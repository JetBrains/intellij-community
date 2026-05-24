// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import androidx.collection.MutableIntSet
import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginGraph.TargetDependencyScope
import org.jetbrains.intellij.build.productLayout.ContentModule
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.contentName
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.model.error.ImplicitEmbeddedContentModuleError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode

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
      val discovered = productsByName.get(pluginGraph.query { product.name() }) ?: return@forEachProductParallel
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
  val rootsWithIncludeDeps = collectModulesWithIncludeDependencies(spec)
  if (rootsWithIncludeDeps.isEmpty()) return null
  val allowedMissing = spec.allowedMissingDependencies

  val violations = LinkedHashMap<ContentModuleName, LinkedHashSet<ContentModuleName>>()
  val chains = HashMap<ContentModuleName, List<String>>()

  pluginGraph.query {
    // Collect the full set of content-module ids the product already ships so we can
    // cheaply recognise a reached dep as "already declared" (direct product content +
    // all module sets recursively + bundled plugins' content). This complements
    // `containsAvailableContentModule` below without re-walking the graph per dep.
    val productContentModuleIds = MutableIntSet()
    product.containsContent { mod, _ -> productContentModuleIds.add(mod.id) }
    product.includesModuleSet { moduleSet ->
      moduleSet.modulesRecursive { mod -> productContentModuleIds.add(mod.id) }
    }
    product.bundles { plugin ->
      plugin.containsContent { mod, _ -> productContentModuleIds.add(mod.id) }
    }

    // Roots: only embedded modules with includeDependencies=true. Packaging silently
    // packs THIS set's transitive JPS runtime deps into the embedded module's jar.
    val rootTargetNames = LinkedHashSet<String>()
    for (rootName in rootsWithIncludeDeps) {
      val rootModule = contentModule(rootName) ?: continue
      if (!rootModule.hasDescriptor) continue
      val modName = rootModule.name().value
      val targetName = if (modName.endsWith("._test")) modName.removeSuffix("._test") else modName
      rootTargetNames.add(targetName)
    }

    val originByTarget = HashMap<String, String>()
    val parents = HashMap<String, String>()
    val visited = HashSet<String>()
    val queue = ArrayDeque<String>()
    for (rootTargetName in rootTargetNames) {
      if (visited.add(rootTargetName)) {
        queue.add(rootTargetName)
        originByTarget.put(rootTargetName, rootTargetName)
      }
    }

    while (queue.isNotEmpty()) {
      val targetName = queue.removeFirst()
      val targetNode = target(targetName) ?: continue
      val origin = originByTarget.get(targetName) ?: targetName
      targetNode.dependsOn { dep ->
        val scope = dep.scope()
        // PRODUCTION_RUNTIME = COMPILE ∪ RUNTIME; packaging skips TEST and PROVIDED.
        if (scope == TargetDependencyScope.TEST || scope == TargetDependencyScope.PROVIDED) {
          return@dependsOn
        }

        val depTargetName = name(dep.targetId)
        if (!visited.add(depTargetName)) return@dependsOn
        parents.put(depTargetName, targetName)
        originByTarget.put(depTargetName, origin)

        val depContentName = ContentModuleName(depTargetName)
        val depModule = contentModule(depContentName)
        if (depModule != null && depModule.hasDescriptor) {
          if (productContentModuleIds.contains(depModule.id) ||
              product.containsAvailableContentModule(depModule)) {
            // already declared by the product; packaging walks its deps too, so BFS continues.
            queue.add(depTargetName)
            return@dependsOn
          }
          if (depContentName in allowedMissing) {
            // product has explicitly accepted this implicit pull via its allowlist; stop BFS
            // here just like the packaging runtime check does.
            return@dependsOn
          }
          // violation — fix is to declare explicitly. Don't traverse into its subtree:
          // once declared, its transitive deps become the module's own problem.
          violations.computeIfAbsent(depContentName) { LinkedHashSet() }.add(ContentModuleName(origin))
          chains.putIfAbsent(depContentName, buildChain(depTargetName, origin, parents))
          return@dependsOn
        }

        // non-content target (legacy JPS module without descriptor): flow through.
        queue.add(depTargetName)
      }
    }
  }

  if (violations.isEmpty()) return null
  return ImplicitEmbeddedContentModuleError(
    context = pluginGraph.query { product.name() },
    missingModules = violations,
    chains = chains,
  )
}

private fun buildChain(from: String, to: String, parents: Map<String, String>): List<String> {
  val result = ArrayList<String>()
  var current: String? = from
  val seen = HashSet<String>()
  while (current != null && seen.add(current)) {
    result.add(current)
    if (current == to) break
    current = parents.get(current)
  }
  result.reverse()
  return result
}

private fun collectModulesWithIncludeDependencies(spec: ProductModulesContentSpec): Set<ContentModuleName> {
  val result = LinkedHashSet<ContentModuleName>()
  for (moduleSetWithOverrides in spec.moduleSets) {
    collectFromModuleSet(moduleSetWithOverrides.moduleSet, result)
  }
  for (m in spec.additionalModules) {
    if (m.includeDependencies) result.add(m.contentName())
  }
  return result
}

private fun collectFromModuleSet(moduleSet: ModuleSet, result: LinkedHashSet<ContentModuleName>) {
  for (m: ContentModule in moduleSet.modules) {
    if (m.includeDependencies) result.add(m.contentName())
  }
  for (nested in moduleSet.nestedSets) {
    collectFromModuleSet(nested, result)
  }
}
