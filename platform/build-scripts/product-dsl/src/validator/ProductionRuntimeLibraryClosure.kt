// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import java.util.BitSet

/**
 * Gives the production runtime libraries of a JPS module and of its module dependencies, with one memo for all modules.
 *
 * The result is equal to `JpsJavaExtensionService.dependencies(module).recursively().includedIn(PRODUCTION_RUNTIME).libraries`.
 * The rules are these:
 * - A dependency with the COMPILE or RUNTIME scope counts.
 * - A dependency with the PROVIDED or TEST scope does not count.
 * - A dependency without a Java dependency extension counts.
 * - The exported flag has no effect.
 * - A library dependency adds its library. An unresolved library adds nothing.
 * - A module dependency adds the result of its module. An unresolved module adds nothing.
 * - All modules of a dependency cycle have the same result.
 *
 * The class assumes that no `JpsJavaDependenciesEnumerationHandler` stops the recursion.
 * An instance is not thread-safe.
 */
internal class ProductionRuntimeLibraryClosure {
  private val libraryToIndex = HashMap<JpsLibrary, Int>()
  private val libraries = ArrayList<JpsLibrary>()
  private val nodes = HashMap<JpsModule, ModuleNode>()
  private var nextNodeIndex = 0

  /**
   * Returns the library at the index that a set from [libraryIndices] holds.
   */
  fun library(index: Int): JpsLibrary = libraries.get(index)

  /**
   * Returns the closure of [module] as a set of library indices.
   *
   * The caller must not change the returned set.
   */
  fun libraryIndices(module: JpsModule): BitSet {
    nodes.get(module)?.closure?.let {
      return it
    }

    // an iterative Tarjan walk: the members of one strongly connected component share one closure
    val componentStack = ArrayList<ModuleNode>()
    val callStack = ArrayList<ModuleNode>()
    callStack.add(createNode(module, componentStack))
    while (callStack.isNotEmpty()) {
      val node = callStack.last()
      if (node.nextDependency < node.dependencies.size) {
        val dependency = node.dependencies.get(node.nextDependency++)
        val dependencyNode = nodes.get(dependency)
        if (dependencyNode == null) {
          callStack.add(createNode(dependency, componentStack))
        }
        else if (dependencyNode.onStack) {
          node.lowLink = minOf(node.lowLink, dependencyNode.index)
        }
        else {
          node.ownLibraries!!.or(dependencyNode.closure!!)
        }
        continue
      }

      callStack.removeLast()
      if (node.lowLink == node.index) {
        val closure = BitSet()
        do {
          val member = componentStack.removeLast()
          closure.or(member.ownLibraries!!)
          member.ownLibraries = null
          member.onStack = false
          member.closure = closure
        }
        while (member !== node)
      }

      val parent = callStack.lastOrNull() ?: continue
      parent.lowLink = minOf(parent.lowLink, node.lowLink)
      // a node without a closure belongs to the component of the parent, and the component merges it at its end
      node.closure?.let {
        parent.ownLibraries!!.or(it)
      }
    }
    return nodes.get(module)!!.closure!!
  }

  private fun createNode(module: JpsModule, componentStack: MutableList<ModuleNode>): ModuleNode {
    val ownLibraries = BitSet()
    val dependencies = ArrayList<JpsModule>()
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    for (element in module.dependenciesList.dependencies) {
      if (!isInProductionRuntime(element, javaExtensionService)) {
        continue
      }
      when (element) {
        is JpsLibraryDependency -> element.library?.let { ownLibraries.set(indexOf(it)) }
        is JpsModuleDependency -> element.module?.let { dependencies.add(it) }
        else -> {}
      }
    }

    val node = ModuleNode(index = nextNodeIndex++, dependencies = dependencies, ownLibraries = ownLibraries)
    nodes.put(module, node)
    componentStack.add(node)
    return node
  }

  private fun indexOf(library: JpsLibrary): Int {
    return libraryToIndex.getOrPut(library) {
      libraries.add(library)
      libraries.size - 1
    }
  }
}

private fun isInProductionRuntime(element: JpsDependencyElement, javaExtensionService: JpsJavaExtensionService): Boolean {
  val scope = javaExtensionService.getDependencyExtension(element)?.scope ?: return true
  return scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
}

private class ModuleNode(
  @JvmField val index: Int,
  @JvmField val dependencies: List<JpsModule>,
  @JvmField var ownLibraries: BitSet?,
) {
  @JvmField var lowLink: Int = index
  @JvmField var onStack: Boolean = true
  @JvmField var nextDependency: Int = 0
  @JvmField var closure: BitSet? = null
}
