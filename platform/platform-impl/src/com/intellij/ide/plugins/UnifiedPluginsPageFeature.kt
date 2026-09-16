// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.registry.RegistryManager

internal object UnifiedPluginsPageFeature {
  const val REGISTRY_KEY: String = "plugin.manager.unified.page"
  const val DENSITY_REGISTRY_KEY: String = "plugin.manager.unified.page.density.options"

  fun isEnabled(): Boolean = RegistryManager.getInstance().`is`(REGISTRY_KEY)

  fun densityVariant(): UnifiedPluginsPageDensityVariant {
    val value = RegistryManager.getInstance().get(DENSITY_REGISTRY_KEY)
    return when {
      value.isOptionEnabled("32 px icons") -> UnifiedPluginsPageDensityVariant.Icons32
      value.isOptionEnabled("2 preview rows") -> UnifiedPluginsPageDensityVariant.TwoPreviewRows
      else -> UnifiedPluginsPageDensityVariant.Baseline
    }
  }
}

internal enum class UnifiedPluginsPageDensityVariant(
  val pluginIconScale: Float,
  val collapsedItemLimit: Int,
) {
  Baseline(pluginIconScale = 1.0f, collapsedItemLimit = 3),
  Icons32(pluginIconScale = 0.8f, collapsedItemLimit = 3),
  TwoPreviewRows(pluginIconScale = 1.0f, collapsedItemLimit = 2),
}
