// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layout

// todo: check if mergeable with MinimapGeometry
data class MinimapLayoutMetrics(
  val lineCount: Int,
  val baseLineHeight: Double,
  val pxPerColumn: Double,
  val contentStartX: Double,
  val contentWidth: Double,
)
