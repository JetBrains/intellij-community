// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.viewer

import com.intellij.collaboration.async.collectWithPrevious
import com.intellij.collaboration.ui.codereview.diff.EditorComponentInlaysManager
import com.intellij.collaboration.ui.codereview.diff.EditorLineInlaysController
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
fun <VM : EditorMapped> EditorEx.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: CoroutineScope.(VM) -> JComponent
) {
  val inlaysManager = EditorComponentInlaysManager(this as EditorImpl)
  EditorLineInlaysController(cs, vmsFlow, vmKeyExtractor, inlaysManager, componentFactory)
}

@ApiStatus.Experimental
fun EditorEx.controlGutterIconsIn(cs: CoroutineScope, createRenderer: (Int) -> GutterIconRenderer) {
  cs.launch(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) {
    lineCountFlow().distinctUntilChanged().collectLatest { editorLineCount ->
      coroutineScope {
        val highlighters = mutableListOf<RangeHighlighter>()
        val renderers = ArrayList<GutterIconRenderer>()

        for (editorLine in 0 until editorLineCount) {
          val renderer = createRenderer(editorLine)
          renderers.add(renderer)
          val rangeHighlighter = markupModel.addLineHighlighter(null, editorLine, HighlighterLayer.LAST).apply {
            gutterIconRenderer = renderer
          }
          highlighters.add(rangeHighlighter)
        }

        controlRenderersIconVisibilityIn(this, renderers)

        try {
          awaitCancellation()
        }
        catch (e: Exception) {
          for (highlighter in highlighters) {
            highlighter.dispose()
          }
        }
      }
    }
  }
}

private fun EditorEx.controlRenderersIconVisibilityIn(cs: CoroutineScope, renderers: List<GutterIconRenderer>) {
  cs.launch(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) {
    hoveredLineFlow().distinctUntilChanged().collectWithPrevious(-1) { prev, curr ->
      if (prev >= 0 && prev < renderers.size) {
        (renderers[prev] as? LineHoverAwareGutterMark)?.let {
          it.isHovered = false
          repaintGutterForLine(it.line)
        }
      }
      if (curr >= 0 && curr < renderers.size) {
        (renderers[curr] as? LineHoverAwareGutterMark)?.let {
          it.isHovered = true
          repaintGutterForLine(it.line)
        }
      }
    }
  }
}

private fun EditorEx.repaintGutterForLine(line: Int) {
  val gutter = gutter as JComponent
  val y = logicalPositionToXY(LogicalPosition(line, 0)).y
  gutter.repaint(0, y, gutter.width, y + lineHeight)
}

private fun EditorEx.lineCountFlow(ignoreLastLf: Boolean = true): Flow<Int> =
  callbackFlow {
    val listener = object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        trySend(getLineCount(ignoreLastLf))
      }
    }
    document.addDocumentListener(listener)
    send(getLineCount(ignoreLastLf))
    awaitClose { document.removeDocumentListener(listener) }
  }

private fun EditorEx.getLineCount(ignoreLastLf: Boolean): Int {
  val lineCount = document.lineCount
  return if (ignoreLastLf && document.immutableCharSequence.lastOrNull() == '\n') lineCount - 1 else lineCount
}

private fun EditorEx.hoveredLineFlow(): Flow<Int> =
  callbackFlow {
    val listener = object : EditorMouseListener, EditorMouseMotionListener {
      override fun mouseMoved(e: EditorMouseEvent) {
        trySend(e.logicalPosition.line)
      }

      override fun mouseExited(e: EditorMouseEvent) {
        trySend(-1)
      }
    }

    addEditorMouseListener(listener)
    addEditorMouseMotionListener(listener)
    send(-1)
    awaitClose {
      removeEditorMouseListener(listener)
      removeEditorMouseMotionListener(listener)
    }
  }

interface EditorMapped {
  val line: Flow<Int?>
  val isVisible: Flow<Boolean>
}