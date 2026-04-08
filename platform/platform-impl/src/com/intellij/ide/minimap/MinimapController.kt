// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.caret.MinimapCaretController
import com.intellij.ide.minimap.geometry.MinimapGeometryCalculator
import com.intellij.ide.minimap.geometry.MinimapScaleUtil
import com.intellij.ide.minimap.layout.MinimapLayoutCalculator
import com.intellij.ide.minimap.listeners.MinimapStateListeners
import com.intellij.ide.minimap.listeners.MinimapUiListeners
import com.intellij.ide.minimap.model.MinimapModel
import com.intellij.ide.minimap.scene.MinimapSceneBuilder
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Graphics2D
import javax.swing.JPanel
import kotlin.math.max

@OptIn(FlowPreview::class)
class MinimapController(
  coroutineScope: CoroutineScope,
  private val panel: MinimapPanel,
  private val container: JPanel,
): Disposable {
  @Volatile
  private var disposed = false

  private val scope = coroutineScope.childScope("MinimapController")
  private val structureUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val diagnosticsUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val scrollUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val settings = MinimapSettings.getInstance()
  private val editor: Editor = panel.editor

  private val model = MinimapModel(editor).also {
    Disposer.register(this, it)
  }

  private val layoutCalculator = MinimapLayoutCalculator(editor)
  private val geometryCalculator = MinimapGeometryCalculator(editor)
  private val sceneBuilder = MinimapSceneBuilder(editor, model, layoutCalculator, geometryCalculator)
  private val caretController = MinimapCaretController(editor, panel)

  private val stateListeners = MinimapStateListeners(
    parentDisposable = this,
    editor = editor,
    caretController = caretController,
    scheduleStructureMarkersUpdate = ::scheduleStructureMarkersUpdate,
    scheduleDiagnosticsUpdate = { scheduleDiagnosticsUpdate() },
    scheduleBreakpointsUpdate = { scheduleDiagnosticsUpdate() },
    scheduleFoldingUpdate = { scheduleDiagnosticsUpdate() },
    invalidateLineProjection = model::invalidateLineProjection,
    updateParameters = ::refreshSnapshot,
      repaint = panel::repaint,
  )

  private val uiListeners = MinimapUiListeners(
    parentDisposable = this,
    container = container,
    contentComponent = editor.contentComponent,
    updateParameters = ::refreshSnapshot,
    revalidate = panel::revalidate,
    repaint = panel::repaint
  )

  fun install() {
    stateListeners.install()
    uiListeners.install()
    refreshSnapshot()
    initStructureMarkersFlow()
    initDiagnosticsFlow()
    // TODO: scroll optimization — re-enable once the fast-path approach is validated
    // initScrollUpdatesFlow()
  }

  override fun dispose() {
    disposed = true
    sceneBuilder.clear()
    scope.cancel()
  }

  fun isDocumentCommitted(): Boolean = model.isDocumentCommitted()

  fun paintCaret(graphics: Graphics2D): Unit = caretController.paint(graphics)

  fun scheduleStructureMarkersUpdate(): Boolean = structureUpdates.tryEmit(Unit)

  fun scheduleDiagnosticsUpdate(): Boolean = diagnosticsUpdates.tryEmit(Unit)

  fun refreshSnapshot() {
    val state = settings.state
    val panelHeight = max(if (panel.height > 0) panel.height else container.height, 0)
    val scaleData = MinimapScaleUtil.computeScale(editor, panelHeight, state.width, state.scaleMode)
    if (!updatePanelVisibility(scaleData.width)) {
      return
    }
    if (panel.updatePreferredWidth(scaleData.width)) {
      panel.revalidate()
    }

    val panelWidth = max(panel.width, scaleData.width)
    val snapshot = sceneBuilder.buildSnapshot(panelWidth, panelHeight, scaleData, state.scaleMode, MinimapRegistry.isLegacy())
    panel.updateSnapshot(snapshot)
  }

  /**
   * Fast scroll update path. Called on every pure vertical scroll event (width/height unchanged).
   *
   * When `areaStart` hasn't changed (the minimap fits entirely in the panel — the common case),
   * the stored token/diagnostic/fold/breakpoint entries are still valid because their y-coordinates
   * are computed relative to `areaStart`. We just copy the snapshot with updated geometry (O(1)).
   *
   * When `areaStart` changes (large file where the minimap itself scrolls), we immediately apply
   * a geometry-only copy so the thumb tracks correctly, then schedule a debounced full rebuild
   * to recompute entries for the new visible window.
   */
  fun refreshOnScroll() {
    val state = settings.state
    val panelHeight = max(if (panel.height > 0) panel.height else container.height, 0)
    val scaleData = MinimapScaleUtil.computeScale(editor, panelHeight, state.width, state.scaleMode)
    if (!updatePanelVisibility(scaleData.width)) return
    if (panel.updatePreferredWidth(scaleData.width)) {
      panel.revalidate()
    }

    val panelWidth = max(panel.width, scaleData.width)
    val lineProjection = model.getLineProjection()
    val newGeometry = geometryCalculator.compute(panelHeight, scaleData, state.scaleMode, lineProjection.projectedLineCount)

    val current = panel.currentSnapshot()
    if (current != null && newGeometry.areaStart == current.geometry.areaStart) {
      // Fast path: visible window didn't shift, only thumb position changed.
      val newContext = current.context.copy(panelWidth = panelWidth, panelHeight = panelHeight, geometry = newGeometry)
      panel.updateSnapshot(current.copy(context = newContext, geometry = newGeometry))
      return
    }

    // areaStart changed — the visible set of lines shifted. Apply geometry immediately so the
    // thumb moves correctly, then schedule a full layout rebuild via debounced flow.
    if (current != null) {
      val newContext = current.context.copy(panelWidth = panelWidth, panelHeight = panelHeight, geometry = newGeometry)
      panel.updateSnapshot(current.copy(context = newContext, geometry = newGeometry))
    }
    scrollUpdates.tryEmit(Unit)
  }

  private fun updatePanelVisibility(minimapWidth: Int): Boolean {
    val hiddenForNarrowEditor = shouldHideForNarrowEditor(minimapWidth)
    val shouldBeVisible = !hiddenForNarrowEditor
    if (panel.isVisible == shouldBeVisible) return shouldBeVisible
    panel.isVisible = shouldBeVisible
    container.revalidate()
    container.repaint()
    return shouldBeVisible
  }

  private fun shouldHideForNarrowEditor(minimapWidth: Int): Boolean {
    if (minimapWidth <= 0) return false
    val editorWidth = container.width
    if (editorWidth <= 0) return false
    return editorWidth.toLong() <= minimapWidth.toLong() * HIDE_MINIMAP_EDITOR_WIDTH_MULTIPLIER
  }

  fun updateStructureMarkersNow() {
    ReadAction.nonBlocking<Unit> { model.updateStructureMarkers() }
      .coalesceBy(this)
      .expireWith(this)
      .finishOnUiThread(ModalityState.any()) {
        refreshSnapshot()
        panel.repaint()
      }.submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun initStructureMarkersFlow() = scope.launch {
    structureUpdates.debounce(STRUCTURE_MARKERS_DEBOUNCE_MS).collect {
      updateStructureMarkersNow()
    }
  }

  private fun initDiagnosticsFlow() = scope.launch {
    diagnosticsUpdates.debounce(DIAGNOSTICS_DEBOUNCE_MS).collect {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        if (disposed) return@withContext
        refreshSnapshot()
        panel.repaint()
      }
    }
  }

  private fun initScrollUpdatesFlow() = scope.launch {
    scrollUpdates.debounce(SCROLL_LAYOUT_DEBOUNCE_MS).collect {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        if (disposed) return@withContext
        refreshSnapshot()
        panel.repaint()
      }
    }
  }

  companion object {
    private const val STRUCTURE_MARKERS_DEBOUNCE_MS: Long = 125
    private const val DIAGNOSTICS_DEBOUNCE_MS: Long = 125
    private const val SCROLL_LAYOUT_DEBOUNCE_MS: Long = 50
    private const val HIDE_MINIMAP_EDITOR_WIDTH_MULTIPLIER: Long = 2
  }
}
