// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.openapi.util.registry.Registry

internal object MinimapRegistry {
  private enum class MinimapMode { DISABLED, LEGACY, NEW }
  private const val MODE_KEY = "editor.minimap.mode"

  private fun mode(): MinimapMode = try {
    val value = Registry.get(MODE_KEY).selectedOption
    MinimapMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MinimapMode.DISABLED
  }
  catch (_: Exception) {
    MinimapMode.DISABLED
  }

  fun isEnabled(): Boolean = mode() != MinimapMode.DISABLED
  fun isLegacy(): Boolean = mode() == MinimapMode.LEGACY
}
