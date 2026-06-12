// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.pluginGraph.TargetName

private const val MODULE_SET_PLUGIN_MODULE_PREFIX: String = "intellij.moduleSet.plugin."

private val HAND_WRITTEN_MODULE_SET_PLUGIN_MODULES: Set<String> = setOf(
  "intellij.grid.core.plugin",
  "intellij.java.aetherDependencyResolver.plugin",
  "intellij.libraries.misc.plugin",
  "intellij.platform.bookmarks.plugin",
  "intellij.platform.execution.serviceView.plugin",
  "intellij.platform.navbar.plugin",
  "intellij.platform.recentFiles.plugin",
  "intellij.platform.structuralSearch.plugin",
  "intellij.platform.structureView.plugin",
  "intellij.platform.ssh.plugin",
  "intellij.platform.todo.plugin",
  "intellij.platform.vcs.split.plugin",
)

fun moduleSetPluginModuleName(moduleSetName: String): TargetName {
  return TargetName(MODULE_SET_PLUGIN_MODULE_PREFIX + moduleSetName)
}

fun isModuleSetPluginModuleName(moduleName: String): Boolean {
  return moduleName.startsWith(MODULE_SET_PLUGIN_MODULE_PREFIX) || moduleName in HAND_WRITTEN_MODULE_SET_PLUGIN_MODULES
}
