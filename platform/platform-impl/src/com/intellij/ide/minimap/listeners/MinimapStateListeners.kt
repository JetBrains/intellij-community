// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.listeners

import com.intellij.ide.minimap.MinimapRegistry
import com.intellij.ide.minimap.breakpoints.MinimapBreakpointUtil
import com.intellij.ide.minimap.caret.MinimapCaretController
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.ErrorStripeEvent
import com.intellij.openapi.editor.ex.ErrorStripeListener
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import java.awt.Rectangle

class MinimapStateListeners(
  private val parentDisposable: Disposable,
  private val editor: Editor,
  private val caretController: MinimapCaretController,
  private val scheduleStructureMarkersUpdate: () -> Unit,
  private val scheduleDiagnosticsUpdate: () -> Unit,
  private val scheduleBreakpointsUpdate: () -> Unit,
  private val scheduleFoldingUpdate: () -> Unit,
  private val invalidateLineProjection: () -> Unit,
  private val updateParameters: () -> Unit,
  private val repaint: () -> Unit,
) {
  private val documentListener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      if (MinimapRegistry.isLegacy()) return
      invalidateLineProjection()
      scheduleStructureMarkersUpdate()
    }
  }

  private val selectionListener = object : SelectionListener {
    override fun selectionChanged(e: SelectionEvent) {
      if (MinimapRegistry.isLegacy()) return
      repaint()
    }
  }

  private val caretListener = object : CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
      if (event.caret != editor.caretModel.primaryCaret) return

      val newOffset = event.caret.offset
      ApplicationManager.getApplication().invokeLater {
        if (editor.isDisposed) return@invokeLater
        caretController.caretMoved(newOffset)
      }
    }
  }

  private val visibleAreaListener = object : VisibleAreaListener {
    private var visibleArea = Rectangle(0, 0, 0, 0)

    override fun visibleAreaChanged(e: VisibleAreaEvent) {
      val newArea = e.newRectangle
      if (visibleArea.y == newArea.y &&
          visibleArea.height == newArea.height &&
          visibleArea.width == newArea.width) {
        return
      }
      visibleArea = newArea
      updateParameters()
      repaint()
    }
  }

  private val errorStripeListener = object : ErrorStripeListener {
    override fun errorMarkerChanged(e: ErrorStripeEvent) {
      if (MinimapRegistry.isLegacy()) return
      scheduleDiagnosticsUpdate()
    }
  }

  private val breakpointMarkupListener = object : MarkupModelListener {
    override fun afterAdded(highlighter: RangeHighlighterEx) {
      onHighlighterChanged(highlighter)
    }

    override fun afterRemoved(highlighter: RangeHighlighterEx) {
      onHighlighterChanged(highlighter)
    }

    override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean, fontStyleOrColorChanged: Boolean) {
      if (!renderersChanged && !fontStyleOrColorChanged) return
      onHighlighterChanged(highlighter)
    }

    private fun onHighlighterChanged(highlighter: RangeHighlighterEx) {
      if (MinimapRegistry.isLegacy()) return
      if (!MinimapBreakpointUtil.isBreakpointHighlighter(highlighter)) return
      scheduleBreakpointsUpdate()
    }
  }

  private val foldingListener = object : FoldingListener {
    override fun onFoldProcessingEnd() {
      if (MinimapRegistry.isLegacy()) return
      invalidateLineProjection()
      scheduleFoldingUpdate()
    }
  }

  fun install() {
    editor.scrollingModel.addVisibleAreaListener(visibleAreaListener, parentDisposable)
    editor.selectionModel.addSelectionListener(selectionListener, parentDisposable)
    editor.caretModel.addCaretListener(caretListener, parentDisposable)
    editor.document.addDocumentListener(documentListener, parentDisposable)
    (editor.markupModel as? EditorMarkupModel)?.addErrorMarkerListener(errorStripeListener, parentDisposable)
    (editor.foldingModel as? FoldingModelEx)?.addListener(foldingListener, parentDisposable)
    (editor as? EditorEx)?.let { editorEx ->
      editorEx.markupModel.addMarkupModelListener(parentDisposable, breakpointMarkupListener)
      editorEx.filteredDocumentMarkupModel.addMarkupModelListener(parentDisposable, breakpointMarkupListener)
    }
  }
}
