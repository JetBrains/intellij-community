// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * [findFileInModuleSources] over one directory listing per asked directory.
 *
 * A descriptor search asks many modules for one file, and a run repeats the same search for each product. Each
 * probe costs one file system call per source root. The index reads the directory of the file once per source root
 * and answers every later probe from memory. A run reads the checkout and writes it only at the end, so a listing
 * stays valid for the whole run.
 *
 * The names compare as the file system stores them. A run on a case-insensitive file system gives the same answer
 * as a run on a case-sensitive one.
 */
@ApiStatus.Internal
class ModuleSourceFileIndex(private val modules: List<JpsModule>) {
  private val listings = ConcurrentHashMap<Path, Set<String>>()
  private val roots = ConcurrentHashMap<JpsModule, Array<SourceRoot>>()
  private val owners = ConcurrentHashMap<String, List<JpsModule>>()

  /** The same answer as [org.jetbrains.intellij.build.findFileInModuleSources]. */
  fun find(module: JpsModule, relativePath: String, onlyProductionSources: Boolean): Path? {
    val normalizedPath = relativePath.trimStart('/')
    for (root in rootsOf(module)) {
      if (onlyProductionSources && !root.isProduction) {
        continue
      }
      if (!normalizedPath.startsWith(root.prefix)) {
        continue
      }
      val pathInRoot = normalizedPath.substring(root.prefix.length)
      val slash = pathInRoot.lastIndexOf('/')
      val directory = if (slash < 0) root.path else root.path.resolve(pathInRoot.substring(0, slash))
      val name = if (slash < 0) pathInRoot else pathInRoot.substring(slash + 1)
      if (name.isEmpty() || name == "." || name == ".." || !childNames(directory).contains(name)) {
        continue
      }
      return root.path.resolve(pathInRoot)
    }
    return null
  }

  /** The modules whose production sources hold [relativePath], in the order of [modules]. Computed once per path. */
  fun findOwners(relativePath: String): List<JpsModule> {
    owners.get(relativePath)?.let { return it }
    val result = modules.filter { find(module = it, relativePath = relativePath, onlyProductionSources = true) != null }
    return owners.putIfAbsent(relativePath, result) ?: result
  }

  /** The source roots of [module] in the order [findFileInModuleSources] asks them, with the path and the prefix resolved once. */
  private fun rootsOf(module: JpsModule): Array<SourceRoot> {
    roots.get(module)?.let { return it }
    val result = ArrayList<SourceRoot>()
    for (type in rootTypeOrder) {
      for (root in module.sourceRoots) {
        if (root.rootType == type) {
          result.add(SourceRoot(
            isProduction = type == JavaResourceRootType.RESOURCE || type == JavaSourceRootType.SOURCE,
            path = root.path,
            prefix = prefixOf(root),
          ))
        }
      }
    }
    val array = result.toTypedArray()
    return roots.putIfAbsent(module, array) ?: array
  }

  private fun childNames(directory: Path): Set<String> {
    listings.get(directory)?.let { return it }
    val result = try {
      Files.newDirectoryStream(directory).use { stream -> stream.mapTo(HashSet()) { it.fileName.toString() } }
    }
    catch (_: IOException) {
      emptySet()
    }
    return listings.putIfAbsent(directory, result) ?: result
  }

  private class SourceRoot(@JvmField val isProduction: Boolean, @JvmField val path: Path, @JvmField val prefix: String)
}

/** The path prefix a source root adds to a relative path: the package prefix or the relative output path, with a trailing slash. */
private fun prefixOf(root: JpsModuleSourceRoot): String {
  val properties = root.properties
  val prefix = when (properties) {
    is JavaSourceRootProperties -> properties.packagePrefix.replace('.', '/')
    is JavaResourceRootProperties -> properties.relativeOutputPath
    else -> ""
  }.trimStart('/')
  return if (prefix.isEmpty() || prefix.endsWith("/")) prefix else "$prefix/"
}
