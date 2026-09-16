// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.PluginLayout
import java.util.TreeMap
import java.util.TreeSet

/**
 * The `PluginLayout` facts of one plugin, as the dev-distribution derivation reads them.
 *
 * Every field is the value of one layout accessor. No field is a difference against a build. The derivation reads
 * these facts beside the project model and answers the jar of every member itself. [pluginLayoutFacts] builds the
 * union over every layout of one main module.
 */
@ApiStatus.Internal
class PluginLayoutFacts(
  /** The plugin's directory under `plugins/`. */
  @JvmField val directoryName: String,
  /** The plugin's main jar, with the `.jar` suffix, under the plugin's `lib/`. */
  @JvmField val mainJarName: String,
  /**
   * Every `withModule` item of the layout, by member: the jar paths under the plugin's `lib/` the layout puts it in.
   *
   * A path equal to [mainJarName] means a plain `withModule(name)`. A member of the plugin's own `<content>` is here
   * too when the layout names it. The main module's own item is not here.
   */
  @JvmField val memberJars: Map<String, Set<String>> = emptyMap(),
  /** Members whose module libraries the layout keeps out of their jar; `doNotCopyModuleLibrariesAutomatically`. */
  @JvmField val unmergedMembers: Set<String> = emptySet(),
  /** Module libraries the layout takes out of a member's jar, by member; `excludeModuleLibrary`. */
  @JvmField val excludedModuleLibraries: Map<String, Set<String>> = emptyMap(),
  /** Project libraries the layout packs, by name, valued by the out path a `withProjectLibrary(name, jarName)` states. */
  @JvmField val projectLibraries: Map<String, String?> = emptyMap(),
  /** Module libraries the layout packs as jars of their own; `withModuleLibrary`. */
  @JvmField val moduleLibraries: List<LayoutModuleLibrary> = emptyList(),
  /** Project libraries an opaque resource generator reads; `withGeneratedResources(inputProjectLibraries, ...)`. */
  @JvmField val generatorLibraries: Set<String> = emptySet(),
  /** Whether a layout scrambles a path of this plugin. A scrambled plugin embeds no content module jar. */
  @JvmField val noEmbedding: Boolean = false,
  /**
   * Whether a layout of this plugin is `auto`, which packs the main module's own dependency group; see [autoLayoutChildren].
   *
   * A plugin with no layout is `auto`, because the build gives such a plugin an `auto` layout.
   */
  @JvmField val auto: Boolean = false,
)

/** One `withModuleLibrary` call: the owning module, the library, and the path the layout gives the jar, or `null`. */
@ApiStatus.Internal
class LayoutModuleLibrary(
  @JvmField val moduleName: String,
  @JvmField val libraryName: String,
  @JvmField val relativeOutputPath: String?,
)

/**
 * Where a plugin's own jars go: its directory under `plugins/` and its main jar under `lib/`.
 *
 * Two tokens, because `PluginLayout` decides the two independently. A plugin that renames its directory usually
 * renames its main jar with it, and `JavaEE` renames only the jar.
 */
@ApiStatus.Internal
class PluginJarPlacement(
  @JvmField val directory: String,
  @JvmField val mainJarName: String,
)

/**
 * The placement `PluginLayout` gives a plugin that states none, from the main module name alone.
 *
 * The derivation owns this copy of the convention, so that the packaging gate compares two producers. The build
 * side spells the same rule in `PluginLayout`, and the gate is what keeps the two in step.
 */
@ApiStatus.Internal
fun pluginJarPlacementConvention(mainModule: String): PluginJarPlacement {
  val directory = mainModule.removePrefix("intellij.").replace('.', '-')
  return PluginJarPlacement(directory = directory, mainJarName = "$directory.jar")
}

/**
 * The union of every layout of [mainModule] over the products and the bundling variants, as one [PluginLayoutFacts].
 *
 * A `PluginLayout` is a fact about a plugin, and a product that states one more member states one more fact. So the
 * member and library sets are unions, in sorted order. Two layouts that disagree on the directory name or the main
 * jar name fail with an [IllegalStateException] that names the plugin: the derivation needs one placement.
 *
 * An empty [layouts] gives [pluginJarPlacementConvention], no member, and an `auto` layout.
 */
@ApiStatus.Internal
fun pluginLayoutFacts(mainModule: String, layouts: List<PluginLayout>): PluginLayoutFacts {
  if (layouts.isEmpty()) {
    val placement = pluginJarPlacementConvention(mainModule)
    return PluginLayoutFacts(directoryName = placement.directory, mainJarName = placement.mainJarName, auto = true)
  }
  val directoryNames = layouts.mapTo(TreeSet()) { it.directoryName }
  check(directoryNames.size == 1) {
    "Plugin `$mainModule`: its layouts disagree on the directory name (${directoryNames.joinToString { "`$it`" }})"
  }
  val mainJarNames = layouts.mapTo(TreeSet()) { it.getMainJarName() }
  check(mainJarNames.size == 1) {
    "Plugin `$mainModule`: its layouts disagree on the main jar name (${mainJarNames.joinToString { "`$it`" }})"
  }
  val memberJars = TreeMap<String, TreeSet<String>>()
  val unmergedMembers = TreeSet<String>()
  val excludedModuleLibraries = TreeMap<String, TreeSet<String>>()
  val projectLibraries = TreeMap<String, String?>()
  val moduleLibraries = TreeSet(MODULE_LIBRARY_ORDER)
  val generatorLibraries = TreeSet<String>()
  var noEmbedding = false
  for (layout in layouts) {
    require(layout.mainModule == mainModule) {
      "Plugin `$mainModule`: a layout of `${layout.mainModule}` is not a layout of this plugin"
    }
    for (item in layout.includedModules) {
      if (item.moduleName != mainModule) {
        memberJars.computeIfAbsent(item.moduleName) { TreeSet() }.add(item.relativeOutputFile)
      }
    }
    unmergedMembers.addAll(layout.getModulesWithExcludedModuleLibraries())
    for ((member, libraries) in layout.getExcludedModuleLibraries()) {
      excludedModuleLibraries.computeIfAbsent(member) { TreeSet() }.addAll(libraries)
    }
    for (library in layout.getIncludedProjectLibraries()) {
      // A path beats no path, so two layouts that state one library once with a path keep the path.
      if (projectLibraries.get(library.libraryName) == null) {
        projectLibraries.put(library.libraryName, library.outPath)
      }
    }
    for ((moduleName, libraryName, relativeOutputPath) in layout.getIncludedModuleLibraries()) {
      moduleLibraries.add(
        LayoutModuleLibrary(
          moduleName = moduleName,
          libraryName = libraryName,
          relativeOutputPath = relativeOutputPath.ifEmpty { null },
        )
      )
    }
    generatorLibraries.addAll(layout.getResourceGeneratorProjectLibraries())
    if (layout.pathsToScramble.isNotEmpty()) {
      noEmbedding = true
    }
  }
  return PluginLayoutFacts(
    directoryName = directoryNames.single(),
    mainJarName = mainJarNames.single(),
    memberJars = memberJars,
    unmergedMembers = unmergedMembers,
    excludedModuleLibraries = excludedModuleLibraries,
    projectLibraries = projectLibraries,
    moduleLibraries = moduleLibraries.toList(),
    generatorLibraries = generatorLibraries,
    noEmbedding = noEmbedding,
    auto = layouts.any { it.auto },
  )
}

/** The sorted order of [PluginLayoutFacts.moduleLibraries]: owner, library, then path. */
private val MODULE_LIBRARY_ORDER: Comparator<LayoutModuleLibrary> =
  compareBy({ it.moduleName }, { it.libraryName }, { it.relativeOutputPath ?: "" })
