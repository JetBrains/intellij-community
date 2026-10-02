// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import java.util.Collections
import java.util.TreeMap
import java.util.TreeSet

/** A quoted string that holds `//`: a label, including one with a `{platform}` token. */
private val QUOTED_LABEL = Regex("\"([^\"\\s]*//[^\"\\s]*)\"")

/**
 * The plan files and the `dev_dist_complex_plugin` calls that the community half writes into the own package of a
 * community plugin.
 *
 * The community half renders first and writes them. The ultimate half cannot write a community package, so it homes a
 * community plugin in the product package. It reuses the plan files and the calls of the own package together
 * when both are equal, see [acceptsUpstreamPlans]. So the ultimate half writes no copy.
 */
internal class DevDistOwnPackagePlans(
  /** The plan home of every community plugin whose plan files sit in its own package, keyed by main module. */
  private val homes: Map<String, DevDistPluginPlanHome>,
  /** The text of every plan file of such a home, keyed by main module and then by file name. */
  private val planFiles: Map<String, Map<String, String>>,
  /** The calls that the own `dev` section of such a plugin states, keyed by main module. */
  private val sectionCalls: Map<String, String>,
  /** The directory of the half that wrote the plans, relative to the monorepo root. Every home directory is relative to it. */
  private val rootDirectory: String = "",
) {
  /** Whether the plan files of [mainModule] sit in its own package. */
  fun hasHome(mainModule: String): Boolean = homes.containsKey(mainModule)

  /** Whether the own section of [mainModule] states the section calls of [calls]. */
  fun acceptsCalls(mainModule: String, calls: DevDistPluginCallRendering): Boolean {
    return calls.sectionText == null || calls.sectionText == sectionCalls.get(mainModule)
  }

  /** The calls the own section of [mainModule] states, or `null`. For a failure message. */
  fun sectionCalls(mainModule: String): String? = sectionCalls.get(mainModule)

  /**
   * Whether the ultimate half can reuse the plan files and the calls that the community half writes into the own
   * package of [mainModule].
   *
   * [planTexts] are the plan files of the ultimate half in its product package, keyed by file name, with
   * their ultimate spelling. [crossHalfCalls] are its calls there. The plan texts and the calls must name only community
   * call labels, see [isCommunityCallLabel]. Such a label can name a repository of the community calls.
   * The plan texts with every `@community//` label spelled `//` must equal the community texts. The community calls with
   * every `//` label spelled `@community//` must equal [crossHalfCalls]. A plan file and a call name no package of their
   * own, so the text of either does not depend on its package.
   */
  fun acceptsUpstreamPlans(mainModule: String, planTexts: Map<String, String>, crossHalfCalls: String?): Boolean {
    if (!homes.containsKey(mainModule) || crossHalfCalls == null) {
      return false
    }
    val communityCalls = sectionCalls.get(mainModule) ?: return false
    val repositories = callRepositories(communityCalls)
    val labels = (planTexts.values + crossHalfCalls).asSequence().flatMap { QUOTED_LABEL.findAll(it) }.map { it.groupValues.get(1) }
    if (labels.any { !isCommunityCallLabel(it, callRepositories = repositories) }) {
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
    return DevDistPluginPlanHome(
      directory = directory,
      packageLabel = home.packageLabel,
      callIsCrossHalf = false,
      exportsPlanFiles = false,
      callRepositories = sectionCalls.get(mainModule)?.let(::callRepositories).orEmpty(),
    )
  }

  /** The repositories that the community calls [calls] name, sorted. A folded call keeps the platform token in a name. */
  private fun callRepositories(calls: String): Set<String> {
    return QUOTED_LABEL.findAll(calls).mapNotNullTo(TreeSet()) { labelRepository(it.groupValues.get(1)) }
  }

  companion object {
    /**
     * Reads the plans that [half] writes into the own package of a plugin: its plan files [files] and its rendered calls
     * [rendering]. The community half records every such home with the text of every plan file there. The ultimate half
     * records none, because no half reads its plans.
     */
    fun of(files: DevDistPluginPlanFiles, rendering: DevDistPluginExecutionRendering, half: DevDistHalf): DevDistOwnPackagePlans {
      val homes = TreeMap<String, DevDistPluginPlanHome>()
      val planFiles = TreeMap<String, Map<String, String>>()
      val sectionCalls = TreeMap<String, String>()
      if (half.writesCommunityPackages) {
        for ((mainModule, home) in files.homes) {
          if (!home.isModulePackage || home.callIsCrossHalf) {
            continue
          }
          homes.put(mainModule, home)
          planFiles.put(mainModule, files.files.entries
            .filter { (path, _) -> path.substringBeforeLast('/', missingDelimiterValue = "") == home.directory }
            .associateTo(TreeMap()) { (path, text) -> path.substringAfterLast('/') to text })
          rendering.calls.get(mainModule)?.sectionText?.let { sectionCalls.put(mainModule, it) }
        }
      }
      return DevDistOwnPackagePlans(
        homes = Collections.unmodifiableMap(homes),
        planFiles = Collections.unmodifiableMap(planFiles),
        sectionCalls = Collections.unmodifiableMap(sectionCalls),
        rootDirectory = half.rootDirectory,
      )
    }
  }
}
