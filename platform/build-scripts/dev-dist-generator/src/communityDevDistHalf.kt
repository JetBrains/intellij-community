// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.annotations.ApiStatus

/**
 * The community half: the products of `community/build/dev-build.json`, written under `community/`.
 *
 * The half has no capability. So it plans no reference plan, no platform patch, no embedded frontend and no runtime
 * module repository, and its binder binds no closed layout source.
 */
@ApiStatus.Internal
object CommunityDevDistHalf : DevDistHalf {
  override val name: String
    get() = "community"

  override val rootDirectory: String
    get() = COMMUNITY_ROOT_DIRECTORY

  override val macrosBzl: String
    get() = "//build:intellij_dev_community.bzl"

  override val jpsBridge: String
    get() = "jps_dynamic_deps_community"

  /**
   * The community binary, see `CommunityDevDistGenerator`. The label is valid in both roots, because the community module
   * is `community`. The ultimate tool writes the same bytes, because it runs this half in the same way.
   */
  override val generatorCommand: String
    get() = "bazel run @community//build:dev_dist_generator"

  /**
   * IDEA Community is the key `Idea`, whose name folds to the name of another key on a case-insensitive file system, so
   * it carries a case-safe name. The order is the order of the same keys in every half that extends this one.
   */
  override val splitDistributions: Map<String, SplitDevDistribution> = linkedMapOf(
    "AndroidStudio" to SplitDevDistribution(),
    "Idea" to SplitDevDistribution(caseSafeName = "idea_community"),
  ).also { products ->
    checkSplitProductNamesAreCaseSafe(products.mapValues { it.value.caseSafeName })
  }

  override val capabilities: Set<DevDistCapability>
    get() = emptySet()

  override val assetBinder: DevDistAssetBinder = RefusingDevDistAssetBinder(name)

  override val embeddedFrontend: DevDistEmbeddedFrontendSupport?
    get() = null

  override val platformPatches: DevDistPlatformPatchSupport?
    get() = null

  override val generatedModuleSetDescriptors: Map<String, String> = linkedMapOf(
    "platform/platform-resources/generated/META-INF" to "intellij.platform.resources",
  )

  override val rowFieldProperties: Map<String, String>
    get() = emptyMap()

  override val refusedRowProperties: Map<String, String>
    get() = emptyMap()

  override fun baseIdeaProperties(product: String, languageServerBase: Boolean): String {
    check(!languageServerBase) { "The $name half has one base idea.properties only, so it cannot plan the other base of '$product'" }
    return COMMUNITY_IDEA_PROPERTIES
  }

  /** Every package below the community root is a package of the half. */
  override fun ownsPackage(directory: String): Boolean = true
}

/** Whether [directory], a path relative to the monorepo root, is the root of the community half or a directory below it. */
@ApiStatus.Internal
fun isCommunityDirectory(directory: String): Boolean {
  return directory == COMMUNITY_ROOT_DIRECTORY || directory.startsWith("$COMMUNITY_ROOT_DIRECTORY/")
}

/** The directory of the community checkout, relative to the monorepo root. */
internal const val COMMUNITY_ROOT_DIRECTORY: String = "community"

/** The base `idea.properties` of every product whose launch model selects no second base. */
internal const val COMMUNITY_IDEA_PROPERTIES: String = "@community//bin:idea.properties"
