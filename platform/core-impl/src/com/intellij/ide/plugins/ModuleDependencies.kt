// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * A dependency from [plugins] in fact means a module dependency on the *implicit main module* of the plugin.
 */
@ApiStatus.Internal
class ModuleDependencies(
  val modules: List<PluginModuleId>,
  val plugins: List<PluginId>,
) {
  @ApiStatus.Internal
  companion object {
    val EMPTY: ModuleDependencies = ModuleDependencies(Collections.emptyList(), Collections.emptyList())
  }

  override fun toString(): String = "ModuleDependencies(modules=${modules.joinToString()}, plugins=${plugins.joinToString()})"
}