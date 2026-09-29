// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * The repository half that one pass of the dev-distribution generator writes, as the paths of the pass.
 *
 * The ultimate pass writes under the monorepo root and owns every `dev <module>` section. The community pass writes the
 * declarations of the community products under `community/`. It reads the community registry, the community run
 * configurations and the community JPS model, and it writes no `dev` section. Under
 * [DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES], each pass writes the files of its own packages instead, see
 * [writesPackage]. Every fact of a half that is not a path comes from [half], see [DevDistHalf].
 *
 * Every project-relative path that a pass computes stays relative to [projectRoot], the monorepo root, in both passes.
 * So a descriptor row or a package path has one spelling, and the community pass renders a `dev` section exactly as the
 * ultimate pass does. A path turns relative to [outputRoot] only when a pass writes it, see [outputRelativePath].
 */
@ApiStatus.Internal
class DevDistGenerationRoot private constructor(
  /** The monorepo root. Every project-relative path of the pass is relative to it. */
  @JvmField val projectRoot: Path,
  /** The half of the pass. */
  @JvmField internal val half: DevDistHalf,
  /** Which half writes the generated files of a community package, see [DevDistOwnership]. */
  @JvmField val ownership: DevDistOwnership,
) {
  /** The root of the half: the pass reads `build/dev-build.json` and `.idea/runConfigurations` here, and writes every output here. */
  @JvmField
  val outputRoot: Path = if (half.rootDirectory.isEmpty()) projectRoot else projectRoot.resolve(half.rootDirectory)

  /**
   * Whether the generated packages of this pass are community packages, so that a label is spelled for a community
   * dependent. A half below the monorepo root is the community half.
   */
  @JvmField
  val dependentIsCommunity: Boolean = half.rootDirectory.isNotEmpty()

  /** The community checkout inside the monorepo. */
  @JvmField
  val communityRoot: Path = projectRoot.resolve(COMMUNITY_ROOT_DIRECTORY)

  /** The `.bzl` file that exports `intellij_dev_run_configurations` to the generated rows file of this half. */
  val macrosBzl: String
    get() = half.macrosBzl

  /** The JPS bridge extension of this half, which resolves a module name of a generated file to a label. */
  val jpsBridge: String
    get() = half.jpsBridge

  /** The directory of the `DevMainKt` run configurations of this half. */
  @JvmField
  val runConfigurationsDir: Path = outputRoot.resolve(".idea/runConfigurations")

  /**
   * Whether this pass writes the `dev <module>` sections. Under [DevDistOwnership.ULTIMATE_WRITES_COMMUNITY_SECTIONS],
   * only the ultimate pass writes them, so a second writer cannot flip them. Under
   * [DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES], each half writes the sections of its own packages.
   */
  @JvmField
  val writesDevSections: Boolean = ownership == DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES || !dependentIsCommunity

  /**
   * Whether this pass writes the generated files in the package of a community module: a `dev` section, a
   * `content_module_jar` call and a plan file. Exactly one half does, see [DevDistOwnership].
   */
  @JvmField
  val writesCommunityModulePackages: Boolean = when (ownership) {
    DevDistOwnership.ULTIMATE_WRITES_COMMUNITY_SECTIONS -> !dependentIsCommunity
    DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES -> dependentIsCommunity
  }

  /**
   * Whether this pass writes the generated files of the package in [directory], a path relative to the monorepo root.
   * Under [DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES], a pass writes only the packages of its half, see
   * [DevDistHalf.ownsPackage].
   */
  fun writesPackage(directory: String): Boolean {
    return when (ownership) {
      DevDistOwnership.ULTIMATE_WRITES_COMMUNITY_SECTIONS -> !dependentIsCommunity || half.ownsPackage(directory)
      DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES -> half.ownsPackage(directory)
    }
  }

  /**
   * Fails when this pass writes [outputRelativePath], a path relative to [outputRoot], into a package that it does not
   * write, see [writesPackage]. The message names the path.
   */
  fun requireWritable(outputRelativePath: String) {
    val projectRelativePath = if (half.rootDirectory.isEmpty()) outputRelativePath else "${half.rootDirectory}/$outputRelativePath"
    val directory = projectRelativePath.substringBeforeLast('/', missingDelimiterValue = "")
    check(writesPackage(directory)) {
      "The $passName cannot write '$projectRelativePath', because the package '$directory' belongs to the other half"
    }
  }

  /** The name of the pass in a census line and in an error message. */
  val passName: String
    get() = "${half.name} pass"

  /**
   * The split products of this half: every split product for the ultimate pass, and the split products of
   * [registryProducts] for the community pass. [registryProducts] are the keys of `build/dev-build.json` under [outputRoot].
   */
  fun splitProducts(registryProducts: Collection<String>): Set<String> {
    val all = half.splitProducts
    if (!dependentIsCommunity) {
      return all
    }
    val registry = registryProducts.toHashSet()
    return all.filterTo(LinkedHashSet()) { it in registry }
  }

  /**
   * [label] as a package of this half writes it. The community pass drops the repository of a `@community//` label, as
   * [DevDistBazelIndex.respellLibraryLabel] does. Every other label stays as it is.
   */
  fun respellLabel(label: String): String {
    if (!dependentIsCommunity || !label.startsWith(COMMUNITY_REPOSITORY_PREFIX)) {
      return label
    }
    return "//" + label.removePrefix(COMMUNITY_REPOSITORY_PREFIX)
  }

  /**
   * The path of [projectRelativePath] relative to [outputRoot]. The community pass removes the `community/` prefix, and
   * it fails for a path outside `community/`, because a community file cannot name such a path.
   */
  fun outputRelativePath(projectRelativePath: String): String {
    if (!dependentIsCommunity) {
      return projectRelativePath
    }
    require(half.ownsPackage(projectRelativePath)) {
      "The $passName cannot write the path '$projectRelativePath', because it is outside ${half.rootDirectory}/"
    }
    return projectRelativePath.removePrefix(half.rootDirectory + "/")
  }

  /**
   * [text] with every quoted `@community//` label respelled for this half, see [respellLabel]. The community pass applies
   * it to a generated file before it writes the file, so no generated community file names the community repository.
   */
  fun respellQuotedLabels(text: String): String {
    return if (dependentIsCommunity) text.replace("\"$COMMUNITY_REPOSITORY_PREFIX", "\"//") else text
  }

  companion object {
    /** The pass of [half] under the monorepo root [projectRoot], with the package ownership [ownership]. */
    fun of(projectRoot: Path, half: DevDistHalf, ownership: DevDistOwnership = DevDistOwnership.DEFAULT): DevDistGenerationRoot {
      return DevDistGenerationRoot(projectRoot = projectRoot, half = half, ownership = ownership)
    }

    /** The pass that writes under `community/` of the monorepo root [projectRoot], with the package ownership [ownership]. */
    fun community(projectRoot: Path, ownership: DevDistOwnership = DevDistOwnership.DEFAULT): DevDistGenerationRoot {
      return of(projectRoot, CommunityDevDistHalf, ownership)
    }
  }
}
