// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.breakpoints.MinimapBreakpointPainter
import com.intellij.ide.minimap.diagnostics.MinimapDiagnosticsPainter
import com.intellij.ide.minimap.folding.MinimapFoldMarkerPainter
import com.intellij.ide.minimap.hover.MinimapHoverController
import com.intellij.ide.minimap.layers.MinimapLayerRenderState
import com.intellij.ide.minimap.legacy.MinimapLegacyPreview
import com.intellij.ide.minimap.paint.MinimapSelectionPainter
import com.intellij.ide.minimap.render.MinimapRenderer
import com.intellij.ide.minimap.thumb.MinimapThumb
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.ui.components.ScrollBarPainter
import java.awt.Color
import java.awt.Graphics2D

internal class MinimapLayerPainter(
  private val editor: Editor,
  private val minimapController: MinimapController,
  private val hoverController: MinimapHoverController,
  repaintRequest: () -> Unit,
) {
  private val renderer = MinimapRenderer()
  private val selectionPainter = MinimapSelectionPainter(editor)
  private val diagnosticsPainter = MinimapDiagnosticsPainter(editor)
  private val foldPainter = MinimapFoldMarkerPainter()
  private val breakpointPainter = MinimapBreakpointPainter()
  private val legacyPreview = MinimapLegacyPreview(repaintRequest)

  fun clear() {
    legacyPreview.clear()
  }

  fun updateLegacyPreview(minimapHeight: Int) {
    legacyPreview.update(editor, minimapHeight, true)
  }

  fun paintLegacyPreviewLayer(graphics: Graphics2D, state: MinimapLayerRenderState) {
    legacyPreview.paint(graphics, editor, state.panelWidth, state.snapshot.geometry)
  }

  fun paintTokenFillerLayer(graphics: Graphics2D, state: MinimapLayerRenderState) {
    val snapshot = state.snapshot
    renderer.paint(graphics, snapshot.context, snapshot.tokenEntries, snapshot.layoutMetrics)
  }

  fun paintSelectionLayer(graphics: Graphics2D, state: MinimapLayerRenderState) {
    val snapshot = state.snapshot
    selectionPainter.paint(graphics, snapshot.context, snapshot.layoutMetrics)
  }

  fun paintDiagnosticsLayer(graphics: Graphics2D, state: MinimapLayerRenderState) {
    diagnosticsPainter.paint(graphics, state.snapshot.diagnosticEntries)
  }

  fun paintFoldMarkersLayer(graphics: Graphics2D, state: MinimapLayerRenderState) {
    val snapshot = state.snapshot
    foldPainter.paint(graphics, snapshot.foldEntries, snapshot.breakpointEntries, snapshot.layoutMetrics, foldMarkerColor())
  }

  fun paintBreakpointsLayer(graphics: Graphics2D, state: MinimapLayerRenderState) {
    breakpointPainter.paint(graphics, state.snapshot.breakpointEntries)
  }

  fun paintHoverLayer(graphics: Graphics2D) {
    hoverController.paint(graphics)
  }

  fun paintCaretLayer(graphics: Graphics2D) {
    minimapController.paintCaret(graphics)
  }

  fun paintThumbLayer(graphics: Graphics2D, state: MinimapLayerRenderState) {
    MinimapThumb.paint(graphics, state.panelWidth, state.snapshot.geometry, thumbColor())
  }

  private fun foldMarkerColor(): Color {
    val scheme = editor.colorsScheme
    return scheme.getColor(EditorColors.LINE_NUMBERS_COLOR) ?: scheme.defaultForeground
  }

  private fun thumbColor(): Color {
    val scheme = editor.colorsScheme
    return scheme.getColor(ScrollBarPainter.THUMB_OPAQUE_BACKGROUND)
           ?: scheme.getColor(EditorColors.LINE_NUMBERS_COLOR)
           ?: scheme.defaultForeground
  }
}
