// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.traversal

import androidx.collection.MutableIntSet
import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginGraph.TargetDependencyScope
import org.jetbrains.intellij.build.productLayout.ContentModule
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.contentName

/** Packaging-equivalent JPS runtime closure of embedded modules with `includeDependencies=true`. */
internal data class ImplicitEmbeddedContentModuleAnalysis(
  @JvmField val reachedModules: Map<ContentModuleName, Set<ContentModuleName>>,
  @JvmField val missingModules: Map<ContentModuleName, Set<ContentModuleName>>,
  @JvmField val chains: Map<ContentModuleName, List<String>>,
)

/**
 * Follows the same production-runtime JPS closure as embedded-module packaging.
 *
 * [ImplicitEmbeddedContentModuleAnalysis.reachedModules] contains descriptor-backed modules already declared in the product as well as
 * undeclared modules. This lets validators distinguish an intentional embedded dependency from a
 * module which merely happens to be listed in the same module set.
 */
internal fun GraphScope.analyzeImplicitEmbeddedContentModules(
  product: ProductNode,
  spec: ProductModulesContentSpec,
): ImplicitEmbeddedContentModuleAnalysis {
  val productContentModuleIds = MutableIntSet()
  product.containsContent { module, _ -> productContentModuleIds.add(module.id) }
  product.includesModuleSet { moduleSet ->
    moduleSet.modulesRecursive { module -> productContentModuleIds.add(module.id) }
  }
  product.bundles { plugin ->
    plugin.containsContent { module, _ -> productContentModuleIds.add(module.id) }
  }

  val rootTargetNames = LinkedHashSet<String>()
  for (rootName in collectModulesWithIncludeDependencies(spec)) {
    val rootModule = contentModule(rootName) ?: continue
    if (!rootModule.hasDescriptor) continue
    val moduleName = rootModule.name().value
    rootTargetNames.add(if (moduleName.endsWith("._test")) moduleName.removeSuffix("._test") else moduleName)
  }

  val reachedModules = LinkedHashMap<ContentModuleName, LinkedHashSet<ContentModuleName>>()
  val missingModules = LinkedHashMap<ContentModuleName, LinkedHashSet<ContentModuleName>>()
  val chains = HashMap<ContentModuleName, List<String>>()
  val allowedMissing = spec.allowedMissingDependencies
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
    targetNode.dependsOn { dependency ->
      val scope = dependency.scope()
      if (scope == TargetDependencyScope.TEST || scope == TargetDependencyScope.PROVIDED) {
        return@dependsOn
      }

      val dependencyTargetName = name(dependency.targetId)
      val isNewTarget = visited.add(dependencyTargetName)
      if (isNewTarget) {
        parents.put(dependencyTargetName, targetName)
        originByTarget.put(dependencyTargetName, origin)
      }

      val dependencyContentName = ContentModuleName(dependencyTargetName)
      val dependencyModule = contentModule(dependencyContentName)
      if (dependencyModule != null && dependencyModule.hasDescriptor) {
        val originContentName = ContentModuleName(origin)
        reachedModules.computeIfAbsent(dependencyContentName) { LinkedHashSet() }.add(originContentName)
        if (isNewTarget) {
          chains.putIfAbsent(dependencyContentName, buildImplicitDependencyChain(dependencyTargetName, origin, parents))
        }

        if (productContentModuleIds.contains(dependencyModule.id) || product.containsAvailableContentModule(dependencyModule)) {
          if (isNewTarget) queue.add(dependencyTargetName)
          return@dependsOn
        }
        if (dependencyContentName !in allowedMissing) {
          missingModules.computeIfAbsent(dependencyContentName) { LinkedHashSet() }.add(originContentName)
        }
        return@dependsOn
      }

      if (isNewTarget) queue.add(dependencyTargetName)
    }
  }

  return ImplicitEmbeddedContentModuleAnalysis(
    reachedModules = reachedModules,
    missingModules = missingModules,
    chains = chains,
  )
}

private fun buildImplicitDependencyChain(from: String, to: String, parents: Map<String, String>): List<String> {
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
  for ((moduleSet) in spec.moduleSets) {
    collectFromModuleSet(moduleSet, result)
  }
  for (module in spec.additionalModules) {
    if (module.includeDependencies) result.add(module.contentName())
  }
  return result
}

private fun collectFromModuleSet(moduleSet: ModuleSet, result: LinkedHashSet<ContentModuleName>) {
  for (module: ContentModule in moduleSet.modules) {
    if (module.includeDependencies) result.add(module.contentName())
  }
  for (nested in moduleSet.nestedSets) {
    collectFromModuleSet(nested, result)
  }
}
