// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import java.util.Collections
import java.util.TreeMap

/**
 * The plan files and the `dev_dist_complex_plugin` calls that the ultimate pass writes into the own package of a
 * community plugin.
 *
 * The own package of a community plugin is a community package, so the community pass can name its targets. The
 * community pass reuses the plan files of that package when it plans the same texts, see [reusableHome]. It reuses the
 * calls of that package when it renders the same calls, see [acceptsCalls]. So the community pass writes a copy of
 * neither.
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

  companion object {
    /** Reads the plans of the ultimate pass: its plan files [files] and its rendered calls [rendering]. */
    fun of(files: DevDistPluginPlanFiles, rendering: DevDistPluginExecutionRendering): DevDistOwnPackagePlans {
      val homes = TreeMap<String, DevDistPluginPlanHome>()
      val planFiles = TreeMap<String, Map<String, String>>()
      val sectionCalls = TreeMap<String, String>()
      val exportedFiles = TreeMap<String, Set<String>>()
      for ((mainModule, home) in files.homes) {
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
      )
    }
  }
}
