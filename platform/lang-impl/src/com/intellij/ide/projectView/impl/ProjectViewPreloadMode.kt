// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils

internal enum class ProjectViewPreloadMode {
  OFF,
  REMDEV,
  ON;
  
  companion object {
    @JvmStatic fun isEnabled(): Boolean = when (registryValue()) {
      OFF -> false
      REMDEV -> PlatformUtils.isJetBrainsClient()
      ON -> true
    }

    private fun registryValue(): ProjectViewPreloadMode {
      return try {
        val value = Registry.get("ide.project.view.preload.mode").selectedOption
        entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
      }
      catch (_: Exception) {
        null
      } ?: OFF
    }
  }
}
