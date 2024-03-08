package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer

internal class EditorCellInput(
  private val editor: EditorEx,
  private val inputControllerFactory: ((NotebookCellInlayController?) -> NotebookCellInlayController?)?,
  private val intervalPointer: NotebookIntervalPointer
) {

  private val folding: EditorCellFolding = EditorCellFolding(editor) {
    if (inputControllerFactory == null) {
      toggleTextFolding()
    }
    else {
      toggleFolding(inputControllerFactory)
    }
  }

  private fun toggleFolding(inputControllerFactory: (NotebookCellInlayController?) -> NotebookCellInlayController?) {
    val controller = inputController
    inputController = if (controller != null) {
      Disposer.dispose(controller.inlay)
      toggleTextFolding()
      null
    }
    else {
      toggleTextFolding()
      inputControllerFactory.invoke(inputController)
    }
    folding.bindTo(inputController)
  }

  private fun toggleTextFolding() {
    val interval = intervalPointer.get() ?: error("Invalid interval")
    val startOffset = editor.document.getLineStartOffset(interval.lines.first)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val foldingModel = editor.foldingModel
    val foldRegion = foldingModel.getFoldRegion(startOffset, endOffset)
    if (foldRegion == null) {
      foldingModel.runBatchFoldingOperation {
        val text = editor.document.getText(TextRange(startOffset, endOffset))
        val placeholder = text.lines().drop(1).firstOrNull { it.trim().isNotEmpty() }?.ellipsis(30) ?: "..."
        foldingModel.createFoldRegion(startOffset, endOffset, placeholder, null, true)
      }
    }
    else {
      foldingModel.runBatchFoldingOperation {
        foldingModel.removeFoldRegion(foldRegion)
      }
    }
  }

  internal var inputController: NotebookCellInlayController? = createOrUpdateController()

  private var highlighters: List<RangeHighlighter>? = null

  private var gutterAction: AnAction? = null

  init {
    folding.bindTo(inputController)
    folding.bindTo(intervalPointer)
  }

  fun dispose() {
    folding.dispose()
    inputController?.let { controller -> Disposer.dispose(controller.inlay) }
    disposeExistingHighlighter()
  }

  fun update() {
    inputController = createOrUpdateController()
    updateGutterIcons()
  }

  private fun createOrUpdateController(): NotebookCellInlayController? {
    val actualController = inputControllerFactory?.invoke(inputController)
    if (actualController != inputController) {
      inputController?.let { controller -> Disposer.dispose(controller.inlay) }
      folding.bindTo(actualController)
    }
    return actualController
  }

  private fun updateGutterIcons() {
    val actualController = inputController
    if (actualController != null) {
      val inlay = actualController.inlay
      inlay.putUserData(NotebookCellInlayController.gutterActionKey, gutterAction)
      inlay.update()
    }
    else {
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
  }

  private fun disposeExistingHighlighter() {
    if (highlighters != null) {
      highlighters?.forEach {
        it.dispose()
      }
      highlighters = null
    }
  }

  fun updatePositions() {
    folding.updatePosition()
  }

  fun onViewportChange() {
    inputController?.onViewportChange()
  }

  fun setGutterAction(action: AnAction) {
    gutterAction = action
    updateGutterIcons()
  }
}

private fun String.ellipsis(length: Int): String {
  return if (this.length > length) {
    substring(0, length - 3) + "..."
  }
  else {
    this
  }
}