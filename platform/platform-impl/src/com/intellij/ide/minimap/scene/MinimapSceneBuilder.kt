// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.scene

import com.intellij.ide.minimap.breakpoints.MinimapBreakpointCollector
import com.intellij.ide.minimap.diagnostics.MinimapDiagnosticsCollector
import com.intellij.ide.minimap.folding.MinimapFoldMarkerCollector
import com.intellij.ide.minimap.geometry.MinimapGeometryCalculator
import com.intellij.ide.minimap.geometry.MinimapScaleData
import com.intellij.ide.minimap.layout.MinimapLayoutCalculator
import com.intellij.ide.minimap.layout.MinimapLayoutMode
import com.intellij.ide.minimap.layout.MinimapLayoutModeSelector
import com.intellij.ide.minimap.model.MinimapStructureMarker
import com.intellij.ide.minimap.model.MinimapModel
import com.intellij.ide.minimap.render.MinimapRenderContext
import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.openapi.editor.Editor

class MinimapSceneBuilder(
  private val editor: Editor,
  private val model: MinimapModel,
  private val layoutCalculator: MinimapLayoutCalculator,
  private val geometryCalculator: MinimapGeometryCalculator,
) {
  private val diagnosticsCollector = MinimapDiagnosticsCollector(editor)
  private val breakpointCollector = MinimapBreakpointCollector(editor)
  private val foldCollector = MinimapFoldMarkerCollector()
  @Volatile
  private var lastStructureMarkers: List<MinimapStructureMarker> = emptyList()

  fun buildSnapshot(panelWidth: Int,
                    panelHeight: Int,
                    scaleData: MinimapScaleData,
                    scaleMode: MinimapScaleMode,
                    isLegacy: Boolean): MinimapSnapshot {
    val lineProjection = model.getLineProjection()
    val geometry = geometryCalculator.compute(panelHeight, scaleData, scaleMode, lineProjection.projectedLineCount)
    val context = MinimapRenderContext(
      editor = editor,
      panelWidth = panelWidth,
      panelHeight = panelHeight,
      geometry = geometry,
      lineProjection = lineProjection,
    )

    if (isLegacy) {
      return MinimapSnapshot(
        context = context,
        geometry = geometry,
        tokenEntries = emptyList(),
        structureEntries = emptyList(),
        diagnosticEntries = emptyList(),
        breakpointEntries = emptyList(),
        foldEntries = emptyList(),
        layoutMetrics = null,
        layoutMode = MinimapLayoutMode.EXACT,
      )
    }

    val isCommitted = model.isDocumentCommitted()
    val structureMarkers = if (isCommitted) {
      model.getStructureMarkers().also { lastStructureMarkers = it }
    }
    else {
      lastStructureMarkers
    }

    val layoutMode = MinimapLayoutModeSelector.selectMode(context, scaleMode)
    val layout = layoutCalculator.buildLayout(context, structureMarkers, layoutMode)
    val diagnosticEntries = diagnosticsCollector.buildEntries(context, layout.metrics)
    val breakpointEntries = breakpointCollector.buildEntries(context, layout.metrics, layoutMode)
    val foldEntries = foldCollector.buildEntries(context, layout.metrics)

    return MinimapSnapshot(
      context = context,
      geometry = geometry,
      tokenEntries = layout.tokenEntries,
      structureEntries = layout.structureEntries,
      diagnosticEntries = diagnosticEntries,
      breakpointEntries = breakpointEntries,
      foldEntries = foldEntries,
      layoutMetrics = layout.metrics,
      layoutMode = layoutMode,
    )
  }

  fun clear() {
    lastStructureMarkers = emptyList()
  }
}
