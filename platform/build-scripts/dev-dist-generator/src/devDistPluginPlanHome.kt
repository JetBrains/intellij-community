// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import kotlin.io.path.invariantSeparatorsPathString

/** A quoted label of a plan text: an optional repository, `//`, and the rest up to the closing quote. */
private val PLAN_TEXT_LABEL = Regex("\"((?:@[A-Za-z0-9._+-]+)?//[^\"]*)\"")

/** A Windows device name, which no path component may spell. */
private val RESERVED_PATH_COMPONENT = Regex("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?")

/**
 * The plan home of one complex plugin: the package that holds its plan files.
 *
 * An ultimate plugin keeps its plan files in its own package, beside its `BUILD.bazel`, and its `dev` section holds
 * the call. A community plugin keeps them in its own package when every label its plan texts name is one a community
 * package may name, see [DevDistBazelIndex.canName]. The call sits in the cross-half plugin package then, and the
 * community `dev` section exports the plan files. A community plan that names another repository goes to the
 * cross-half plugin package with its call, so no label of another repository appears under `community/`.
 *
 * The community pass homes a complex plugin in its generated plugin package under `community/`. The plugin's own
 * package holds the plan files of the ultimate pass, and a second writer there would make the two passes alternate.
 * When the own package holds the same plan files, the community pass reads them there and writes no copy, see
 * [DevDistOwnPackagePlans]. Under [DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES], the two roles swap, see
 * [devDistPluginPlanHome].
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
 * Resolves the plan home of [mainModule] over [planTexts], the text of every plan file of the plugin.
 *
 * A module the [index] does not place fails the run: a plan file needs a package to sit in. The census prints one line
 * for a community plugin whose plan goes cross-half, with the first label that sent it there.
 *
 * [writesCommunityModulePackages] says whether the run writes the package of a community module, see
 * [DevDistGenerationRoot.writesCommunityModulePackages]. The community half that writes them homes a complex plugin in
 * its own package with the call in its `dev` section, and the directory is relative to `community/`. The ultimate half
 * that does not write them homes a community plugin in the cross-half plugin package.
 */
internal fun devDistPluginPlanHome(
  mainModule: String,
  planTexts: Collection<String>,
  index: DevDistBazelIndex,
  writesCommunityModulePackages: Boolean = !index.planPackageIsCommunity,
): DevDistPluginPlanHome {
  val location = requireNotNull(index.location(mainModule)) { "Plugin '$mainModule' has no Bazel package, so its plan files have no home" }
  if (index.planPackageIsCommunity && writesCommunityModulePackages) {
    val directory = index.communityRoot.relativize(requireNotNull(index.packageDir(mainModule))).invariantSeparatorsPathString
    return DevDistPluginPlanHome(directory = directory, packageLabel = location.absolutePackage, callIsCrossHalf = false, exportsPlanFiles = false)
  }
  if (index.planPackageIsCommunity || location.half == RepositoryHalf.COMMUNITY && !writesCommunityModulePackages) {
    val directory = crossHalfPackageDirectory(mainModule, product = null)
    return DevDistPluginPlanHome(directory = directory, packageLabel = "//$directory", callIsCrossHalf = true, exportsPlanFiles = false)
  }
  val packageDir = index.projectRoot.relativize(requireNotNull(index.packageDir(mainModule))).invariantSeparatorsPathString
  when (location.half) {
    RepositoryHalf.ULTIMATE -> {
      return DevDistPluginPlanHome(directory = packageDir, packageLabel = location.absolutePackage, callIsCrossHalf = false, exportsPlanFiles = false)
    }
    RepositoryHalf.COMMUNITY -> {
      val foreign = planTexts.asSequence().flatMap(::planTextLabels).firstOrNull { !index.canName(it, dependentIsCommunity = true) }
      if (foreign == null) {
        return DevDistPluginPlanHome(directory = packageDir, packageLabel = location.absolutePackage, callIsCrossHalf = true, exportsPlanFiles = true)
      }
      println("plan home of $mainModule is cross-half: its plan names '$foreign', which a community package cannot name")
      val directory = crossHalfPackageDirectory(mainModule, product = null)
      return DevDistPluginPlanHome(directory = directory, packageLabel = "//$directory", callIsCrossHalf = true, exportsPlanFiles = false)
    }
  }
}

/** Every label-shaped quoted string of [text], in text order. */
internal fun planTextLabels(text: String): Sequence<String> = PLAN_TEXT_LABEL.findAll(text).map { it.groupValues.get(1) }

/** Fails for a path with an empty, relative, or reserved component, or with a character outside `[A-Za-z0-9._+-]`. */
private fun validatePlanPath(path: String) {
  if (path.isEmpty()) return
  require(path.split('/').all { component ->
    component.isNotBlank() && component != "." && component != ".." && !component.endsWith('.') &&
    component.all { it.isLetterOrDigit() || it in "._-+" } && !RESERVED_PATH_COMPONENT.matches(component)
  }) { "Unsafe plan home directory: '$path'" }
}
