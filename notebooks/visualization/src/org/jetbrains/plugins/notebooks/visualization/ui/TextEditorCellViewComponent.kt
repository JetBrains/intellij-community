package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import java.awt.Dimension
import java.awt.Point

class TextEditorCellViewComponent(
  private val editor: EditorEx,
  private val intervalPointer: NotebookIntervalPointer
) : EditorCellViewComponent {

  private var highlighters: List<RangeHighlighter>? = null

  private val cellEventListeners = EventDispatcher.create(EditorCellViewComponentListener::class.java)
  override val location: Point
    get() {
      val interval = intervalPointer.get()
      return if (interval != null) {
        val startOffset = editor.document.getLineStartOffset(interval.lines.first)
        editor.offsetToXY(startOffset)
      }
      else {
        Point(0, 0)
      }
    }

  override val size: Dimension
    get() {
      val interval = intervalPointer.get()
      return if (interval != null) {
        val startOffset = editor.document.getLineStartOffset(interval.lines.first)
        val endOffset = editor.document.getLineEndOffset(interval.lines.last)
        val location = editor.offsetToXY(startOffset)
        val height = editor.offsetToXY(endOffset).y + editor.lineHeight - location.y
        val width = editor.offsetToXY(endOffset).x - location.x
        Dimension(width, height)
      }
      else {
        Dimension(0, 0)
      }
    }

  override fun updateGutterIcons(gutterAction: AnAction?) {
    disposeExistingHighlighter()
    val action = gutterAction
    if (action != null) {
      val markupModel = editor.markupModel
      val interval = intervalPointer.get()!!
      val startOffset = editor.document.getLineStartOffset(interval.lines.first)
      val endOffset = editor.document.getLineEndOffset(interval.lines.last)
      val highlighter = markupModel.addRangeHighlighter(
        startOffset,
        endOffset,
        HighlighterLayer.FIRST - 100,
        TextAttributes(),
        HighlighterTargetArea.LINES_IN_RANGE
      )
      highlighter.gutterIconRenderer = ActionToGutterRendererAdapter(action)
      this.highlighters = listOf(highlighter)
    }
  }

  override fun dispose() {
    disposeExistingHighlighter()
  }

  override fun onViewportChange() {
  }

  private fun disposeExistingHighlighter() {
    if (highlighters != null) {
      highlighters?.forEach {
        it.dispose()
      }
      highlighters = null
    }
  }

  override fun addViewComponentListener(listener: EditorCellViewComponentListener) {
    cellEventListeners.addListener(listener)
  }

  override fun updatePositions() {
    cellEventListeners.multicaster.componentBoundaryChanged(location, size)
  }
}