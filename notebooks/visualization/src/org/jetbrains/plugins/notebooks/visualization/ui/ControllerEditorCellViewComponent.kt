package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController
import org.jetbrains.plugins.notebooks.visualization.UpdateContext
import java.awt.Rectangle

class ControllerEditorCellViewComponent(
  internal val controller: NotebookCellInlayController,
  private val editor: Editor,
  private val cell: EditorCell,
) : EditorCellViewComponent(), HasGutterIcon {


  private var foldedRegion: FoldRegion? = null

  override fun updateGutterIcons(gutterAction: AnAction?) {
    val inlay = controller.inlay
    inlay.putUserData(NotebookCellInlayController.GUTTER_ACTION_KEY, gutterAction)
    inlay.update()
  }

  override fun doDispose() {
    controller.let { controller -> Disposer.dispose(controller.inlay) }
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
    updateContext.addFoldingOperation {
      val doc = editor.document
      val interval = cell.interval

      val cellContentStart = doc.getLineStartOffset(interval.lines.first + 1)
      val cellEnd = doc.getLineEndOffset(interval.lines.last)

      //Configure folding
      val regionToFold = IntRange(cellContentStart, cellEnd)
      val foldingModel = editor.foldingModel as FoldingModelEx

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