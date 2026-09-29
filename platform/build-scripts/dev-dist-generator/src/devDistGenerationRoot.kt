// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * The repository half that one pass of the dev-distribution generator writes, as the paths of the pass.
 *
 * The ultimate pass writes under the monorepo root and owns every `dev <module>` section. The community pass writes the
 * declarations of the community products under `community/`. It reads the community registry, the community run
 * configurations and the community JPS model, and it writes no `dev` section. Every fact of a half that is not a path
 * comes from [half], see [DevDistHalf].
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

  /** Whether this pass writes the `dev <module>` sections. Only the ultimate pass owns them, so a second writer cannot flip them. */
  @JvmField
  val writesDevSections: Boolean = !dependentIsCommunity

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
    /** The pass of [half] under the monorepo root [projectRoot]. */
    fun of(projectRoot: Path, half: DevDistHalf): DevDistGenerationRoot {
      return DevDistGenerationRoot(projectRoot = projectRoot, half = half)
    }

    /** The pass that writes under `community/` of the monorepo root [projectRoot]. */
    fun community(projectRoot: Path): DevDistGenerationRoot = of(projectRoot, CommunityDevDistHalf)
  }
}
