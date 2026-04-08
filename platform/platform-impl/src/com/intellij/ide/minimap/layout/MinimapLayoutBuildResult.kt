// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layout

import com.intellij.ide.minimap.render.MinimapRenderEntry

data class MinimapLayoutBuildResult(
  val tokenEntries: List<MinimapRenderEntry>,
  val structureEntries: List<MinimapRenderEntry>,
  val metrics: MinimapLayoutMetrics?,
) {
  companion object {
    val EMPTY: MinimapLayoutBuildResult = MinimapLayoutBuildResult(
      tokenEntries = emptyList(),
      structureEntries = emptyList(),
      metrics = null,
    )
  }
}