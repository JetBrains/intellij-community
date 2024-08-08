package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.notebooks.visualization.UpdateContext
import org.jetbrains.plugins.notebooks.visualization.ui.EditorEmbeddedComponentLayoutManager.CustomFoldingConstraint
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import javax.swing.JComponent

class CustomFoldingEditorCellViewComponent(
  internal val component: JComponent,
  private val editor: EditorEx,
  private val cell: EditorCell,
) : EditorCellViewComponent(), HasGutterIcon {

  private var foldingRegion: CustomFoldRegion? = null

  private var gutterActionRenderer: ActionToGutterRendererAdapter? = null

  @TestOnly
  fun getComponentForTest(): JComponent {
    return component
  }

  override fun updateGutterIcons(gutterAction: AnAction?) {
    gutterActionRenderer = gutterAction?.let { ActionToGutterRendererAdapter(it) }
    foldingRegion?.update()
  }

  override fun doDispose() {
    disposeFolding()
  }

  private fun disposeFolding() {
    if (editor.isDisposed || foldingRegion?.isValid != true) return
    editor.componentContainer.remove(component)
    foldingRegion?.let {
      editor.foldingModel.runBatchFoldingOperation(
        {
          editor.foldingModel.removeFoldRegion(it)
        }, true, false)
    }
  }

  override fun calculateBounds(): Rectangle {
    return component.bounds
  }

  override fun updateCellFolding(updateContext: UpdateContext) {
    updateContext.addFoldingOperation {
      foldingRegion?.dispose()
      val fr = editor.foldingModel.addCustomLinesFolding(
        cell.interval.lines.first, cell.interval.lines.last, object : CustomFoldRegionRenderer {
        override fun calcWidthInPixels(region: CustomFoldRegion): Int {
          return component.width
        }

        override fun calcHeightInPixels(region: CustomFoldRegion): Int {
          return component.height
        }

        override fun paint(region: CustomFoldRegion, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
        }

        override fun calcGutterIconRenderer(region: CustomFoldRegion): GutterIconRenderer? {
          return gutterActionRenderer
        }
      }) ?: error("Failed to create folding region ${cell.interval.lines}")
      foldingRegion = fr
      editor.componentContainer.add(component, CustomFoldingConstraint(fr, true))
    }
  }
}