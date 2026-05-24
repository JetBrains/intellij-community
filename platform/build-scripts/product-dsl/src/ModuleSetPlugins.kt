// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.pluginGraph.TargetName

private const val MODULE_SET_PLUGIN_MODULE_PREFIX: String = "intellij.moduleSet.plugin."

fun moduleSetPluginModuleName(moduleSetName: String): TargetName {
  return TargetName(MODULE_SET_PLUGIN_MODULE_PREFIX + moduleSetName)
}

fun isModuleSetPluginModuleName(moduleName: String): Boolean {
  return moduleName.startsWith(MODULE_SET_PLUGIN_MODULE_PREFIX)
}
