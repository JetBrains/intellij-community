// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import java.util.Collections
import java.util.TreeMap

/** A quoted string that holds `//`: a label, including one with a `{platform}` token. */
private val QUOTED_LABEL = Regex("\"([^\"\\s]*//[^\"\\s]*)\"")

/**
 * The plan files and the `dev_dist_complex_plugin` calls that the half that renders first writes into the own package
 * of a community plugin.
 *
 * The own package of a community plugin is a community package, so either half can name its targets. Under
 * [DevDistOwnership.ULTIMATE_WRITES_COMMUNITY_SECTIONS], the ultimate pass writes them, and the community pass reuses the
 * plan files of that package when it plans the same texts, see [reusableHome]. It reuses the calls of that package when
 * it renders the same calls, see [acceptsCalls]. Under [DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES], the community
 * half writes them, and the ultimate half reuses the plan files and the calls together when both are equal, see
 * [acceptsUpstreamPlans]. So the half that renders second writes a copy of neither.
 */
internal class DevDistOwnPackagePlans(
  /** The plan home of every community plugin whose plan files sit in its own package, keyed by main module. */
  private val homes: Map<String, DevDistPluginPlanHome>,
  /** The written text of every plan file of such a home, keyed by main module and then by file name. */
  private val planFiles: Map<String, Map<String, String>>,
  /** The calls that the own `dev` section of such a plugin states, keyed by main module. */
  private val sectionCalls: Map<String, String>,
  /** The plan file names that the own `dev` section of such a plugin exports, keyed by main module. */
  private val exportedFiles: Map<String, Set<String>>,
  /** The directory of the half that wrote the plans, relative to the monorepo root. Every home directory is relative to it. */
  private val rootDirectory: String = "",
) {
  /** Whether the plan files of [mainModule] sit in its own package. */
  fun hasHome(mainModule: String): Boolean = homes.containsKey(mainModule)

  /**
   * The home of [mainModule] as the community pass states it, when the own package of the plugin holds the plan files
   * [writtenFiles], or `null`. [writtenFiles] are the baseline files of the community pass, keyed by file name, with the
   * text that the community pass writes. [root] gives the directory relative to `community/`.
   */
  fun reusableHome(mainModule: String, writtenFiles: Map<String, String>, root: DevDistGenerationRoot): DevDistPluginPlanHome? {
    val home = homes.get(mainModule) ?: return null
    if (planFiles.get(mainModule) != writtenFiles) {
      return null
    }
    return DevDistPluginPlanHome(
      directory = root.outputRelativePath(home.directory),
      packageLabel = home.packageLabel,
      callIsCrossHalf = true,
      exportsPlanFiles = true,
    )
  }

  /**
   * Whether the own package of [mainModule] serves the calls [calls] of the community pass. A call in the own section
   * must have the text of the call that the ultimate pass writes there. A call of the generated package that reads a
   * plan file of the own package needs the export of every file in [homeFiles].
   */
  fun acceptsCalls(mainModule: String, calls: DevDistPluginCallRendering, homeFiles: Collection<String>): Boolean {
    if (calls.sectionText != null && calls.sectionText != sectionCalls.get(mainModule)) {
      return false
    }
    return !calls.exportsPlanFiles || exportedFiles.get(mainModule).orEmpty().containsAll(homeFiles)
  }

  /**
   * Whether the ultimate half can reuse the plan files and the calls that the community half writes into the own
   * package of [mainModule].
   *
   * [planTexts] are the plan files of the ultimate half in its cross-half plugin package, keyed by file name, with
   * their ultimate spelling. [crossHalfCalls] are its calls there. The plan texts and the calls must name only community
   * call labels, see [isCommunityCallLabel]. The plan texts must equal the written community texts after the community
   * respelling. The community calls with every `//`
   * label spelled `@community//` must equal [crossHalfCalls]. A plan file and a call name no package of their own, so
   * the text of either does not depend on its package.
   */
  fun acceptsUpstreamPlans(mainModule: String, planTexts: Map<String, String>, crossHalfCalls: String?): Boolean {
    if (!homes.containsKey(mainModule) || crossHalfCalls == null) {
      return false
    }
    val communityCalls = sectionCalls.get(mainModule) ?: return false
    if ((planTexts.values + crossHalfCalls).any { text -> QUOTED_LABEL.findAll(text).any { !isCommunityCallLabel(it.groupValues.get(1)) } }) {
      return false
    }
    val respelled = planTexts.mapValuesTo(TreeMap()) { (_, text) -> text.replace("\"$COMMUNITY_REPOSITORY_PREFIX", "\"//") }
    return planFiles.get(mainModule) == respelled && communityCalls.replace("\"//", "\"$COMMUNITY_REPOSITORY_PREFIX") == crossHalfCalls
  }

  /**
   * The home of [mainModule] in its own package, as the ultimate half names it: the directory relative to the monorepo
   * root, and the call in the community section. The ultimate half writes nothing there, see
   * [DevDistPluginPlanFiles.reusedHomes].
   */
  fun upstreamHome(mainModule: String): DevDistPluginPlanHome {
    val home = checkNotNull(homes.get(mainModule)) { "Plugin '$mainModule' has no plan home in its own package" }
    val directory = listOf(rootDirectory, home.directory).filter { it.isNotEmpty() }.joinToString("/")
    return DevDistPluginPlanHome(directory = directory, packageLabel = home.packageLabel, callIsCrossHalf = false, exportsPlanFiles = false)
  }

  companion object {
    /**
     * Reads the plans of the half of [root]: its plan files [files] and its rendered calls [rendering].
     *
     * The ultimate pass records a home whose section exports the plan files. The community half under
     * [DevDistOwnership.EACH_HALF_OWNS_ITS_PACKAGES] records a home in the own package, with the written text of every
     * plan file there. A `null` [root] reads the plans of the ultimate pass.
     */
    fun of(files: DevDistPluginPlanFiles, rendering: DevDistPluginExecutionRendering, root: DevDistGenerationRoot? = null): DevDistOwnPackagePlans {
      val homes = TreeMap<String, DevDistPluginPlanHome>()
      val planFiles = TreeMap<String, Map<String, String>>()
      val sectionCalls = TreeMap<String, String>()
      val exportedFiles = TreeMap<String, Set<String>>()
      val upstreamRoot = root?.takeIf { it.dependentIsCommunity && it.writesCommunityModulePackages }
      for ((mainModule, home) in files.homes) {
        if (upstreamRoot != null) {
          if (!home.isModulePackage || home.callIsCrossHalf) {
            continue
          }
          homes.put(mainModule, home)
          planFiles.put(mainModule, files.files.entries
            .filter { (path, _) -> path.substringBeforeLast('/', missingDelimiterValue = "") == home.directory }
            .associateTo(TreeMap()) { (path, text) -> path.substringAfterLast('/') to upstreamRoot.respellQuotedLabels(text) })
          rendering.calls.get(mainModule)?.sectionText?.let { sectionCalls.put(mainModule, it) }
          continue
        }
        if (!home.exportsPlanFiles) {
          continue
        }
        val names = files.exportedFiles.get(mainModule).orEmpty()
        homes.put(mainModule, home)
        planFiles.put(mainModule, names.associateWithTo(TreeMap()) { files.files.getValue(home.path(it)) })
        val calls = rendering.calls.get(mainModule) ?: continue
        calls.sectionText?.let { sectionCalls.put(mainModule, it) }
        if (calls.exportsPlanFiles) {
          exportedFiles.put(mainModule, names.toSet())
        }
      }
      return DevDistOwnPackagePlans(
        homes = Collections.unmodifiableMap(homes),
        planFiles = Collections.unmodifiableMap(planFiles),
        sectionCalls = Collections.unmodifiableMap(sectionCalls),
        exportedFiles = Collections.unmodifiableMap(exportedFiles),
        rootDirectory = upstreamRoot?.half?.rootDirectory.orEmpty(),
      )
    }
  }
}
