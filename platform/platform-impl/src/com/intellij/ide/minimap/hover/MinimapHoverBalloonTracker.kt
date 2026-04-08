// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.settings.MinimapSettingsState
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.PositionTracker
import org.jetbrains.annotations.NotNull
import java.awt.Point
import java.awt.Rectangle
import javax.swing.SwingUtilities

class MinimapHoverBalloonTracker(
  private val panel: MinimapPanel,
  private val minimapState: MinimapSettingsState,
  private val rectProvider: () -> Rectangle?,
) : PositionTracker<Balloon>(panel) {
  fun refresh(): Unit = revalidate()

  override fun recalculateLocation(@NotNull balloon: Balloon): RelativePoint {
    val rect = rectProvider() ?: return RelativePoint(panel, Point(0, 0))
    val halfWidth = balloon.preferredSize.width / 2
    val anchorX = if (minimapState.rightAligned) -halfWidth else panel.width + halfWidth
    val anchorY = clampToEditorY(rect.y)

    return RelativePoint(panel, Point(anchorX, anchorY))
  }

  private fun clampToEditorY(anchorY: Int): Int {
    val editorComponent = panel.editor.component

    if (!editorComponent.isShowing) return anchorY
    val editorVisibleRect = editorComponent.visibleRect
    if (editorVisibleRect.isEmpty) return anchorY

    val editorBoundsInPanel = SwingUtilities.convertRectangle(editorComponent, editorVisibleRect, panel)
    if (editorBoundsInPanel.isEmpty) return anchorY

    val minY = editorBoundsInPanel.y
    val maxY = (editorBoundsInPanel.y + editorBoundsInPanel.height - 1).coerceAtLeast(minY)
    return anchorY.coerceIn(minY, maxY)
  }
}
