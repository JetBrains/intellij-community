package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.util.Disposer
import com.intellij.notebooks.visualization.NotebookCellInlayController
import com.intellij.notebooks.visualization.UpdateContext
import java.awt.Rectangle

class ControllerEditorCellViewComponent(
  internal val controller: NotebookCellInlayController,
  private val editor: Editor,
  private val cell: EditorCell,
) : EditorCellViewComponent() {

  private var foldedRegion: FoldRegion? = null

  private fun updateGutterIcons(gutterAction: AnAction?) {
    val inlay = controller.inlay
    inlay.update()
  }

  init {
    cell.gutterAction.afterChange(this) { action ->
      updateGutterIcons(action)
    }
    updateGutterIcons(cell.gutterAction.get())
  }

  override fun dispose() {
    super.dispose()
    Disposer.dispose(controller.inlay)
    disposeFolding()
  }

  private fun disposeFolding() {
    if (editor.isDisposed || foldedRegion?.isValid != true) return
    foldedRegion?.let {
      editor.foldingModel.runBatchFoldingOperation(
        { editor.foldingModel.removeFoldRegion(it) }, true, false)
    }
  }

  override fun doViewportChange() {
    controller.onViewportChange()
  }

  override fun calculateBounds(): Rectangle {
    return controller.inlay.bounds ?: Rectangle(0, 0, 0, 0)
  }

  override fun updateCellFolding(updateContext: UpdateContext) {
    updateContext.addFoldingOperation { foldingModel ->
      val doc = editor.document
      val interval = cell.interval

      val cellContentStart = doc.getLineStartOffset(interval.lines.first + 1)
      val cellEnd = doc.getLineEndOffset(interval.lines.last)

      //Configure folding
      val regionToFold = IntRange(cellContentStart, cellEnd)

      if (foldedRegion != null) {
        disposeFolding()
      }
      foldedRegion = createFoldRegion(foldingModel, regionToFold)
    }
  }

  private fun createFoldRegion(foldingModel: FoldingModelEx, regionToFold: IntRange): FoldRegion? =
    foldingModel.createFoldRegion(regionToFold.first, regionToFold.last, "", null, true)

  override fun doGetInlays(): Sequence<Inlay<*>> {
    return sequenceOf(controller.inlay)
  }
}