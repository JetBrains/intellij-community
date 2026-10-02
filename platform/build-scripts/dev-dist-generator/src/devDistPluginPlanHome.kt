// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import kotlin.io.path.invariantSeparatorsPathString

/** A Windows device name, which no path component may spell. */
private val RESERVED_PATH_COMPONENT = Regex("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?")

/**
 * The plan home of one complex plugin: the package that holds its plan files.
 *
 * A plugin of the half of the run keeps its plan files in its own package, beside its `BUILD.bazel`, and its `dev`
 * section holds the call. The ultimate half cannot write a community package, so it homes a community plugin in the
 * cross-half plugin package with the call. When the plan files and the calls equal the ones of the community half, the
 * ultimate half reads them in the own package and writes no copy, see [DevDistOwnPackagePlans].
 */
internal class DevDistPluginPlanHome(
  /** The project-relative directory: `community/plugins/kotlin/plugin`, `plugins/tailwindcss`, or `build/dev-dist-descriptors/intellij.java.plugin`. */
  @JvmField val directory: String,
  /** The absolute package label the call states: `@community//plugins/kotlin/plugin`, `//plugins/tailwindcss`, or `//build/dev-dist-descriptors/intellij.java.plugin`. */
  @JvmField val packageLabel: String,
  /** Whether the call lives in the cross-half plugin package, and not in the own `dev` section. */
  @JvmField val callIsCrossHalf: Boolean,
  /** Whether the plan files live in a community package while the call lives cross-half, so the `dev` section exports them. */
  @JvmField val exportsPlanFiles: Boolean,
  /**
   * The repositories that the community calls in this home name, with the platform token of a folded call. The ultimate
   * half renders a reused call into this home, and that call can name them, see [isCommunityCallLabel]. Empty for
   * every other home.
   */
  @JvmField val communityRepositories: Set<String> = emptySet(),
) {
  init {
    require(packageLabel.startsWith("//") || packageLabel.startsWith(COMMUNITY_REPOSITORY_PREFIX)) {
      "A plan home is a package of the main repository or of the community half: '$packageLabel'"
    }
    require(':' !in packageLabel) { "A plan home is a package, not a target: '$packageLabel'" }
    require(callIsCrossHalf || !exportsPlanFiles) { "A section exports its plan files only when the call lives cross-half" }
    validatePlanPath(directory)
  }

  /** Whether [directory] is the package of the plugin's main module. The cross-half home is a generated package instead. */
  val isModulePackage: Boolean
    get() = !callIsCrossHalf || exportsPlanFiles

  /** The project-relative path of the plan file [fileName]. */
  fun path(fileName: String): String = if (directory.isEmpty()) fileName else "$directory/$fileName"

  /** The absolute label of the plan file [fileName]. */
  fun label(fileName: String): String = "$packageLabel:$fileName"
}

/**
 * Resolves the plan home of [mainModule].
 *
 * A module the [index] does not place fails the run: a plan file needs a package to sit in.
 *
 * The community half homes a complex plugin in its own package with the call in its `dev` section, and the directory is
 * relative to `community/`. The ultimate half homes an ultimate plugin in its own package, and a community plugin in the
 * cross-half plugin package, because it writes no community package.
 */
internal fun devDistPluginPlanHome(mainModule: String, index: DevDistBazelIndex): DevDistPluginPlanHome {
  val location = requireNotNull(index.location(mainModule)) { "Plugin '$mainModule' has no Bazel package, so its plan files have no home" }
  if (index.planPackageIsCommunity) {
    val directory = index.communityRoot.relativize(requireNotNull(index.packageDir(mainModule))).invariantSeparatorsPathString
    return DevDistPluginPlanHome(directory = directory, packageLabel = location.absolutePackage, callIsCrossHalf = false, exportsPlanFiles = false)
  }
  if (location.half == RepositoryHalf.COMMUNITY) {
    val directory = crossHalfPackageDirectory(mainModule, product = null)
    return DevDistPluginPlanHome(directory = directory, packageLabel = "//$directory", callIsCrossHalf = true, exportsPlanFiles = false)
  }
  val packageDir = index.projectRoot.relativize(requireNotNull(index.packageDir(mainModule))).invariantSeparatorsPathString
  return DevDistPluginPlanHome(directory = packageDir, packageLabel = location.absolutePackage, callIsCrossHalf = false, exportsPlanFiles = false)
}

/** Fails for a path with an empty, relative, or reserved component, or with a character outside `[A-Za-z0-9._+-]`. */
private fun validatePlanPath(path: String) {
  if (path.isEmpty()) return
  require(path.split('/').all { component ->
    component.isNotBlank() && component != "." && component != ".." && !component.endsWith('.') &&
    component.all { it.isLetterOrDigit() || it in "._-+" } && !RESERVED_PATH_COMPONENT.matches(component)
  }) { "Unsafe plan home directory: '$path'" }
}
