// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import javax.swing.JPanel

internal class CellCustomFoldingRender(
  val component: JPanel,
  val gutterIconProvider: () -> GutterIconRenderer?,
) : CustomFoldRegionRenderer {
  override fun calcWidthInPixels(region: CustomFoldRegion): Int {
    val width = component.width
    if (width == 0) {
      //Component can be not valid or not inited so we take preferred size
      return component.preferredSize?.width ?: 0
    }
    return width
  }

  override fun calcHeightInPixels(region: CustomFoldRegion): Int {
    val height = component.height
    if (height == 0) {
      //Component can be not valid or not inited so we take preferred size
      return component.preferredSize?.height ?: 0
    }
    return height
  }

  override fun paint(region: CustomFoldRegion, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) = Unit
  override fun calcGutterIconRenderer(region: CustomFoldRegion) = gutterIconProvider()
}