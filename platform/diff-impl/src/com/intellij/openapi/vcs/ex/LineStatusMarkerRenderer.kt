// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil.DiffStripeTextAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.PeekableIterator
import com.intellij.util.containers.PeekableIteratorWrapper
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent

abstract class LineStatusMarkerRenderer internal constructor(
  @JvmField protected val tracker: LineStatusTrackerI<*>
) {
  private val disposable = tracker.disposable
  private val updateQueue = MergingUpdateQueue("LineStatusMarkerRenderer", 100, true, MergingUpdateQueue.ANY_COMPONENT, disposable)
  private var disposed = false

  private val project = tracker.project
  private val document = tracker.document
  protected fun getRanges() = tracker.getRanges()

  private var gutterHighlighter: RangeHighlighter = createGutterHighlighter()
  private val errorStripeHighlighters: MutableList<RangeHighlighter> = ArrayList()

  protected open val editorFilter: MarkupEditorFilter? = null

  init {
    Disposer.register(disposable, Disposable {
      disposed = true
      destroyHighlighters()
    })
    ApplicationManager.getApplication().getMessageBus().connect(disposable)
      .subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          scheduleValidateHighlighter()
        }
      })
    scheduleUpdate()
  }

  fun scheduleUpdate() {
    updateQueue.queue(DisposableUpdate.createDisposable(updateQueue, "update", Runnable { updateHighlighters() }))
  }

  private fun scheduleValidateHighlighter() {
    // IDEA-246614
    updateQueue.queue(DisposableUpdate.createDisposable(updateQueue, "validate highlighter", Runnable {
      if (disposed || gutterHighlighter.isValid()) return@Runnable
      disposeHighlighter(gutterHighlighter)
      gutterHighlighter = createGutterHighlighter()
      updateHighlighters()
    }))
  }

  private fun createGutterHighlighter(): RangeHighlighter {
    val markupModel = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
    return markupModel.addRangeHighlighterAndChangeAttributes(null, 0, document.textLength,
                                                              DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                              HighlighterTargetArea.LINES_IN_RANGE,
                                                              false) { it: RangeHighlighterEx ->
      it.setGreedyToLeft(true)
      it.setGreedyToRight(true)
      it.setLineMarkerRenderer(MyActiveGutterRenderer())
      val filter = editorFilter
      if (filter != null) it.setEditorFilter(filter)

      // ensure key is there in MarkupModelListener.afterAdded event
      it.putUserData(MAIN_KEY, true)
    }
  }

  @RequiresEdt
  private fun updateHighlighters() {
    if (disposed) return
    EditorFactory.getInstance().editors(document).forEach {
      if (it is EditorEx) {
        it.gutterComponentEx.repaint()
      }
    }
    updateErrorStripeHighlighters()
  }

  @RequiresEdt
  private fun updateErrorStripeHighlighters() {
    val ranges = getRanges()
    if (!shouldPaintErrorStripeMarkers() || ranges.isNullOrEmpty()) {
      for (highlighter in errorStripeHighlighters) {
        disposeHighlighter(highlighter)
      }
      errorStripeHighlighters.clear()
      return
    }

    val markupModel = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
    val highlighterIt: PeekableIterator<RangeHighlighter> = PeekableIteratorWrapper(errorStripeHighlighters.iterator())
    val newHighlighters = mutableListOf<RangeHighlighter>()
    val oldHighlighters = mutableListOf<RangeHighlighter>()
    for (range in ranges) {
      val textRange = DiffUtil.getLinesRange(markupModel.getDocument(), range.line1, range.line2, false)
      while (highlighterIt.hasNext() && highlighterIt.peek().getStartOffset() < textRange.startOffset) {
        oldHighlighters.add(highlighterIt.next())
      }
      val oldHighlighter = if (highlighterIt.hasNext()) highlighterIt.peek() else null
      val oldMarkerData = oldHighlighter?.getUserData(TOOLTIP_KEY)
      if (oldHighlighter != null && oldHighlighter.isValid()
          && oldMarkerData != null && oldMarkerData.type == range.type
          && oldHighlighter.getStartOffset() == textRange.startOffset
          && oldHighlighter.getEndOffset() == textRange.endOffset) {
        // reuse existing highlighter if possible
        newHighlighters.add(oldHighlighter)
        highlighterIt.next()
      }
      else {
        newHighlighters.add(createErrorStripeHighlighter(markupModel, textRange, range.type))
      }
    }

    while (highlighterIt.hasNext()) {
      oldHighlighters.add(highlighterIt.next())
    }

    for (highlighter in oldHighlighters) {
      disposeHighlighter(highlighter)
    }

    errorStripeHighlighters.clear()
    errorStripeHighlighters.addAll(newHighlighters)
  }

  private fun createErrorStripeHighlighter(markupModel: MarkupModelEx, textRange: TextRange, diffType: Byte): RangeHighlighter =
    markupModel.addRangeHighlighterAndChangeAttributes(null, textRange.startOffset, textRange.endOffset,
                                                       DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                       HighlighterTargetArea.LINES_IN_RANGE,
                                                       false) { it: RangeHighlighterEx ->
      it.setThinErrorStripeMark(true)
      it.setGreedyToLeft(true)
      it.setGreedyToRight(true)
      it.setTextAttributes(DiffStripeTextAttributes(diffType))
      val filter = editorFilter
      if (filter != null) it.setEditorFilter(filter)

      // ensure key is there in MarkupModelListener.afterAdded event
      it.putUserData(TOOLTIP_KEY, MarkerData(diffType))
    }

  private fun destroyHighlighters() {
    val gutterHighlighter = gutterHighlighter
    if (!gutterHighlighter.isValid() || gutterHighlighter.getStartOffset() != 0 || gutterHighlighter.getEndOffset() != document.textLength) {
      LOG.warn(String.format("Highlighter is damaged for %s, isValid: %s", this, gutterHighlighter.isValid()))
    }
    disposeHighlighter(gutterHighlighter)
    for (highlighter in errorStripeHighlighters) {
      disposeHighlighter(highlighter)
    }
    errorStripeHighlighters.clear()
  }

  private fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
    val ranges = getSelectedRanges(editor, e.y)
    return !ranges.isEmpty() && canDoAction(editor, ranges, e)
  }

  private fun doAction(editor: Editor, e: MouseEvent) {
    val ranges = getSelectedRanges(editor, e.y)
    if (!ranges.isEmpty()) {
      e.consume()
      doAction(editor, ranges, e)
    }
  }

  private fun getSelectedRanges(editor: Editor, y: Int): List<Range> {
    val ranges = getRanges()
    if (ranges == null) return emptyList()
    return LineStatusMarkerDrawUtil.getSelectedRanges(ranges, editor, y)
  }

  protected open fun canDoAction(editor: Editor, ranges: List<Range>, e: MouseEvent): Boolean = false

  protected open fun doAction(editor: Editor, ranges: List<Range>, e: MouseEvent) {}

  //
  // Gutter painting
  //
  private fun calcBounds(editor: Editor, lineNum: Int, bounds: Rectangle): Rectangle? {
    val ranges = getRanges()
    if (ranges == null) return null
    return LineStatusMarkerDrawUtil.calcBounds(ranges, editor, lineNum)
  }

  /**
   * @return true if gutter markers should be painted, false otherwise
   */
  protected open fun shouldPaintGutter(): Boolean = true

  /**
   * @return true if markers in the error stripe (near the scrollbar) should be painted, false otherwise
   */
  protected open fun shouldPaintErrorStripeMarkers(): Boolean = shouldPaintGutter()

  protected open fun paint(editor: Editor, g: Graphics) {
    val ranges = getRanges() ?: return
    LineStatusMarkerDrawUtil.paintDefault(editor, g, ranges, DefaultFlagsProvider.DEFAULT, 0)
  }

  private inner class MyActiveGutterRenderer : ActiveGutterRenderer, LineMarkerRendererEx {
    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
      if (shouldPaintGutter()) {
        this@LineStatusMarkerRenderer.paint(editor, g)
      }
    }

    override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
      return shouldPaintGutter() && this@LineStatusMarkerRenderer.canDoAction(editor, e)
    }

    override fun doAction(editor: Editor, e: MouseEvent) {
      if (shouldPaintGutter()) {
        this@LineStatusMarkerRenderer.doAction(editor, e)
      }
    }

    override fun calcBounds(editor: Editor, lineNum: Int, preferredBounds: Rectangle): Rectangle? {
      if (!shouldPaintGutter()) return Rectangle(-1, -1, 0, 0)
      return this@LineStatusMarkerRenderer.calcBounds(editor, lineNum, preferredBounds)
    }

    override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.CUSTOM

    override fun getAccessibleName(): String = DiffBundle.message("vcs.marker.changed.line")
  }

  class MarkerData(val type: Byte)

  companion object {
    private val LOG = Logger.getInstance(LineStatusMarkerRenderer::class.java)

    val TOOLTIP_KEY: Key<MarkerData> = Key.create("LineStatusMarkerRenderer.Tooltip.Id")
    val MAIN_KEY: Key<Boolean> = Key.create("LineStatusMarkerRenderer.Main.Id")

    private fun disposeHighlighter(highlighter: RangeHighlighter) {
      try {
        highlighter.dispose()
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }
  }
}
