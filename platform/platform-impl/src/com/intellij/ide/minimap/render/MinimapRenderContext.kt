// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.render

import com.intellij.ide.minimap.geometry.MinimapGeometryData
import com.intellij.ide.minimap.model.MinimapLineProjection
import com.intellij.openapi.editor.Editor

data class MinimapRenderContext(
  val editor: Editor,
  val panelWidth: Int,
  val panelHeight: Int,
  val geometry: MinimapGeometryData,
  val lineProjection: MinimapLineProjection,
)
