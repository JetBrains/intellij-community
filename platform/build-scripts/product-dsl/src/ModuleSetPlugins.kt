// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import kotlinx.serialization.Serializable

private const val MODULE_SET_PLUGIN_MODULE_PREFIX: String = "intellij.moduleSet.plugin."
private const val MODULE_SET_PLUGIN_ID_PREFIX: String = "com.intellij.moduleSet."

/**
 * Marks a module set as being materialized as a standalone bundled plugin.
 *
 * The plugin module name is always derived from the module set name to keep JPS target identity stable.
 */
@Serializable
data class ModuleSetPluginSpec(
  /** Optional plugin ID override. If omitted, a deterministic ID is generated from module set name. */
  val pluginIdOverride: PluginId? = null,
)

fun moduleSetPluginModuleName(moduleSetName: String): TargetName {
  return TargetName(MODULE_SET_PLUGIN_MODULE_PREFIX + moduleSetName)
}

fun defaultModuleSetPluginId(moduleSetName: String): PluginId {
  return PluginId(MODULE_SET_PLUGIN_ID_PREFIX + moduleSetName)
}

fun resolveModuleSetPluginId(moduleSet: ModuleSet): PluginId {
  val spec = requireNotNull(moduleSet.pluginSpec) {
    "Module set '${moduleSet.name}' is not pluginized"
  }
  return spec.pluginIdOverride ?: defaultModuleSetPluginId(moduleSet.name)
}

fun collectPluginizedModuleSets(moduleSets: List<ModuleSet>): List<ModuleSet> {
  val result = ArrayList<ModuleSet>()
  val visited = HashSet<String>()

  fun visit(moduleSet: ModuleSet) {
    if (!visited.add(moduleSet.name)) {
      return
    }
    if (moduleSet.pluginSpec != null) {
      result.add(moduleSet)
    }
    for (nestedSet in moduleSet.nestedSets) {
      visit(nestedSet)
    }
  }

  for (moduleSet in moduleSets) {
    visit(moduleSet)
  }
  return result
}
