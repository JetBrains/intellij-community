package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorGutterColor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.notebooks.visualization.UpdateContext
import org.jetbrains.plugins.notebooks.visualization.ui.EditorEmbeddedComponentLayoutManager.CustomFoldingConstraint
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

class CustomFoldingEditorCellViewComponent(
  internal val component: JComponent,
  private val editor: EditorEx,
  private val cell: EditorCell,
) : EditorCellViewComponent(), HasGutterIcon {

  private var foldingRegion: CustomFoldRegion? = null

  private var gutterActionRenderer: ActionToGutterRendererAdapter? = null

  private val bottomContainer = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
  }
  private val mainComponent = JPanel().also {
    it.layout = BorderLayout()
    it.add(component, BorderLayout.CENTER)
    it.add(bottomContainer, BorderLayout.SOUTH)
  }

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
    editor.componentContainer.remove(mainComponent)
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
      editor.componentContainer.add(mainComponent, CustomFoldingConstraint(fr, true))
    }
  }

  private val presentationToComponent = mutableMapOf<InlayPresentation, JComponent>()

  override fun addInlayBelow(presentation: InlayPresentation) {
    val inlayComponent = object : JComponent() {
      override fun getPreferredSize(): Dimension? {
        return Dimension(presentation.width, presentation.height)
      }

      override fun paint(g: Graphics) {
        g as Graphics2D
        val attributes = TextAttributes().apply {
          backgroundColor = EditorGutterColor.getEditorGutterBackgroundColor(editor as EditorImpl, false)
        }
        presentation.paint(g, attributes)
      }
    }
    inlayComponent.background = EditorGutterColor.getEditorGutterBackgroundColor(editor as EditorImpl, false)
    presentationToComponent[presentation] = inlayComponent
    bottomContainer.add(inlayComponent)
  }

  override fun removeInlayBelow(presentation: InlayPresentation) {
    presentationToComponent.remove(presentation)?.let { bottomContainer.remove(it) }
  }
}