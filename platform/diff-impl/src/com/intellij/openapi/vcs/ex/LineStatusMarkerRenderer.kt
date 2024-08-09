// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil.DiffStripeTextAttributes
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.PeekableIterator
import com.intellij.util.containers.PeekableIteratorWrapper
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue

abstract class LineStatusMarkerRenderer internal constructor(
  protected val project: Project?,
  protected val document: Document,
  protected val disposable: Disposable,
  private val editorFilter: MarkupEditorFilter? = null,
  private val isMain: Boolean = true // tell clients that it's a "proper" vcs status renderer
) {
  private val updateQueue = MergingUpdateQueue("LineStatusMarkerRenderer", 100, true, MergingUpdateQueue.ANY_COMPONENT, disposable)
  private var disposed = false

  private var gutterHighlighter: RangeHighlighter = createGutterHighlighter()
  private val errorStripeHighlighters: MutableList<RangeHighlighter> = ArrayList()

  protected abstract fun getRanges(): List<Range>?

  init {
    Disposer.register(disposable, Disposable {
      disposed = true
      destroyHighlighters()
    })
    val busConnection = ApplicationManager.getApplication().getMessageBus().connect(disposable)
    busConnection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          scheduleValidateHighlighter()
        }
      })
    busConnection.subscribe(PowerSaveMode.TOPIC, object : PowerSaveMode.Listener {
      override fun powerSaveStateChanged() {
        scheduleValidateHighlighter()
      }
    })
    scheduleUpdate()
  }

  fun scheduleUpdate() {
    updateQueue.queue(DisposableUpdate.createDisposable(updateQueue, "update", Runnable {
      WriteIntentReadAction.run {
        updateHighlighters()
      }
    }))
  }

  /**
   * Recover from an evildoer destroying all the highlighters for the Editor/Project/IDE.
   * IDEA-331139 IDEA-246614
   */
  private fun scheduleValidateHighlighter() {
    updateQueue.queue(DisposableUpdate.createDisposable(updateQueue, "validate highlighter", Runnable {
      if (disposed || gutterHighlighter.isValid()) return@Runnable

      LOG.warn("Line marker highlighter was recovered. This incident will be reported.")
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
      it.setLineMarkerRenderer(createGutterMarkerRenderer())
      val filter = editorFilter
      if (filter != null) it.setEditorFilter(filter)

      // ensure key is there in MarkupModelListener.afterAdded event
      it.putUserData(MAIN_KEY, isMain)
    }
  }

  protected open fun createGutterMarkerRenderer(): LineMarkerRenderer = object : LineStatusGutterMarkerRenderer() {
    override fun getPaintedRanges(): List<Range> = getRanges().orEmpty()
  }

  @RequiresEdt
  private fun updateHighlighters() {
    if (disposed) return

    if (!gutterHighlighter.isValid()) {
      scheduleValidateHighlighter()
    }

    try {
      repaintGutter()
      updateErrorStripeHighlighters()
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      throw RuntimeException("Error in $this", e)
    }
  }

  private fun repaintGutter() {
    EditorFactory.getInstance().editors(document).forEach {
      if (it is EditorEx) {
        it.gutterComponentEx.repaint()
      }
    }
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
      if (oldHighlighter != null && oldHighlighter.isValid() &&
          oldMarkerData != null && oldMarkerData.type == range.type &&
          oldHighlighter.startOffset == textRange.startOffset &&
          oldHighlighter.endOffset == textRange.endOffset) {
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

  private fun createErrorStripeHighlighter(markupModel: MarkupModelEx, textRange: TextRange, diffType: Byte): RangeHighlighter {
    return markupModel.addRangeHighlighterAndChangeAttributes(null, textRange.startOffset, textRange.endOffset,
                                                              DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                              HighlighterTargetArea.LINES_IN_RANGE,
                                                              false) { it: RangeHighlighterEx ->
      it.setThinErrorStripeMark(true)
      it.setGreedyToLeft(true)
      it.setGreedyToRight(true)
      it.setTextAttributes(createErrorStripeTextAttributes(diffType))
      val filter = editorFilter
      if (filter != null) it.setEditorFilter(filter)

      // ensure key is there in MarkupModelListener.afterAdded event
      it.putUserData(TOOLTIP_KEY, MarkerData(diffType))
    }
  }

  protected open fun createErrorStripeTextAttributes(diffType: Byte): TextAttributes = DiffStripeTextAttributes(diffType)

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

  /**
   * @return true if markers in the error stripe (near the scrollbar) should be painted, false otherwise
   */
  protected open fun shouldPaintErrorStripeMarkers(): Boolean = true

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
