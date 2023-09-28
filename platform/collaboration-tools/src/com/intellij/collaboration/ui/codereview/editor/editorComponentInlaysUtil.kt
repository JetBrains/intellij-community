// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.collectWithPrevious
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.diff.viewer.LineHoverAwareGutterMark
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent
import javax.swing.JPanel

interface EditorMapped {
  val line: Flow<Int?>
  val isVisible: Flow<Boolean>
}

@ApiStatus.Experimental
fun <VM : EditorMapped> EditorEx.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: CoroutineScope.(VM) -> JComponent
) {
  val editor = this
  val controllersByVmKey: MutableMap<Any, Job> = ConcurrentHashMap()

  cs.launchNow(Dispatchers.Default + CoroutineName("Editor component inlays for $this")) {
    vmsFlow.collect { vms ->
      val vmsByKey = mutableMapOf<Any, VM>()

      for (vm in vms) {
        vmsByKey[vmKeyExtractor(vm)] = vm
      }

      // remove missing
      val iter = controllersByVmKey.iterator()
      while (iter.hasNext()) {
        val (key, job) = iter.next()
        if (!vmsByKey.containsKey(key)) {
          iter.remove()
          job.cancelAndJoinSilently()
        }
      }

      //add new
      for (vm in vms) {
        val key = vmKeyExtractor(vm)
        if (controllersByVmKey.contains(key)) continue
        controllersByVmKey[key] = cs.controlInlay(vm, editor, componentFactory)
      }
    }
  }
}

private fun <VM : EditorMapped> CoroutineScope.controlInlay(
  vm: VM, editor: EditorEx, componentFactory: CoroutineScope.(VM) -> JComponent
): Job = launchNow(Dispatchers.Main) {
  var inlay: Inlay<*>? = null
  try {
    combine(vm.line, vm.isVisible, ::Pair)
      .distinctUntilChanged()
      .collectLatest { (line, isVisible) ->
        val currentInlay = inlay
        if (line != null && isVisible) {
          val offset = editor.document.getLineEndOffset(line)
          if (currentInlay?.isValid != true && currentInlay?.offset != offset) {
            currentInlay?.let(Disposer::dispose)
            inlay = editor.insertComponentAfter(line, componentFactory(vm))
          }
          awaitCancellation()
        }
        else if (currentInlay != null) {
          Disposer.dispose(currentInlay)
          inlay = null
        }
      }
  }
  finally {
    withContext(NonCancellable + ModalityState.any().asContextElement()) {
      inlay?.let(Disposer::dispose)
      inlay = null
    }
  }
}

// TODO: rework diff mode with aligned changes.
//  This mode uses Inlays and some comments may look bad with this mode enabled because of the positioning
/**
 * @param priority impacts the visual order in which inlays are displayed. Components with higher priority will be shown higher
 */
@RequiresEdt
fun EditorEx.insertComponentAfter(lineIndex: Int,
                                  component: JComponent,
                                  priority: Int = 0,
                                  rendererFactory: (Inlay<*>) -> GutterIconRenderer? = { null }): Inlay<*>? {
  val offset = document.getLineEndOffset(lineIndex)
  return insertComponent(offset, component, priority, rendererFactory)
}

@RequiresEdt
fun EditorEx.insertComponent(offset: Int,
                             component: JComponent,
                             priority: Int = 0,
                             rendererFactory: (Inlay<*>) -> GutterIconRenderer? = { null }): Inlay<*>? {
  val editor = this
  val layout = SizeRestrictedSingleComponentLayout().apply {
    // 52 for avatar and gaps
    prefSize = DimensionRestrictions.ScalingConstant(width = CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH + 52)
  }
  val wrappedComponent = JPanel(layout).apply {
    isOpaque = false
    add(component)
  }

  val renderer = object : ComponentInlayRenderer<JComponent>(wrappedComponent, ComponentInlayAlignment.FIT_VIEWPORT_WIDTH) {
    override fun calcGutterIconRenderer(inlay: Inlay<*>): GutterIconRenderer? = rendererFactory(inlay)
  }

  val props = InlayProperties().priority(priority).relatesToPrecedingText(true)
  return editor.addComponentInlay(offset, props, renderer)
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