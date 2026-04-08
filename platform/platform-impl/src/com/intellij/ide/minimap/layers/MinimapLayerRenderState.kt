// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layers

import com.intellij.ide.minimap.scene.MinimapSnapshot

data class MinimapLayerRenderState(
  val snapshot: MinimapSnapshot,
  val panelWidth: Int,
  val isLegacyMode: Boolean,
)
