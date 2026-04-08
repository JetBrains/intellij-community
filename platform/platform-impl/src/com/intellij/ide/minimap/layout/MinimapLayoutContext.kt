// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layout

import com.intellij.ide.minimap.model.MinimapLineProjection
import com.intellij.openapi.editor.Document

internal class MinimapLayoutContext(
  val document: Document,
  val metrics: MinimapLayoutMetrics,
  val areaStart: Double,
  val visibleLines: IntRange,
  val lineProjection: MinimapLineProjection,
)
