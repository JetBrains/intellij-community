package com.intellij.notebooks.visualization.ui

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
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.notebooks.visualization.ui.EditorEmbeddedComponentLayoutManager.CustomFoldingConstraint
import java.awt.AWTEvent.MOUSE_EVENT_MASK
import java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseEvent
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
    background = EditorGutterColor.getEditorGutterBackgroundColor(editor as EditorImpl, false)
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

  private fun disposeFolding() = cell.manager.update { ctx ->
    foldingRegion?.let { region ->
      ctx.addFoldingOperation {
        if (region.isValid == true) {
          editor.foldingModel.removeFoldRegion(region)
        }
      }
    }
    editor.componentContainer.remove(mainComponent)
    foldingRegion = null
  }

  override fun calculateBounds(): Rectangle {
    return foldingRegion?.let { region ->
      region.location?.let { location -> Rectangle(location.x, location.y, region.widthInPixels, region.heightInPixels) }
    } ?: mainComponent.bounds
  }

  override fun updateCellFolding(updateContext: UpdateContext) {
    updateContext.addFoldingOperation {
      foldingRegion?.dispose()
      val fr = editor.foldingModel.addCustomLinesFolding(
        cell.interval.lines.first, cell.interval.lines.last, object : CustomFoldRegionRenderer {
        override fun calcWidthInPixels(region: CustomFoldRegion): Int {
          return mainComponent.width
        }

        override fun calcHeightInPixels(region: CustomFoldRegion): Int {
          return mainComponent.height
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

      init {
        enableEvents(MOUSE_EVENT_MASK or MOUSE_MOTION_EVENT_MASK)
      }

      override fun getPreferredSize(): Dimension? {
        return Dimension(presentation.width, presentation.height)
      }

      override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        g as Graphics2D
        val attributes = TextAttributes()
        presentation.paint(g, attributes)
      }

      override fun processMouseMotionEvent(e: MouseEvent) {
        when (e.id) {
          MouseEvent.MOUSE_MOVED -> presentation.mouseMoved(e, e.point)
        }
      }

      override fun processMouseEvent(e: MouseEvent) {
        when (e.id) {
          MouseEvent.MOUSE_EXITED -> presentation.mouseExited()
          MouseEvent.MOUSE_CLICKED -> presentation.mouseClicked(e, e.point)
          MouseEvent.MOUSE_PRESSED -> presentation.mousePressed(e, e.point)
          MouseEvent.MOUSE_RELEASED -> presentation.mouseReleased(e, e.point)
        }
      }
    }
    presentationToComponent[presentation] = inlayComponent
    bottomContainer.add(inlayComponent)
  }

  override fun removeInlayBelow(presentation: InlayPresentation) {
    presentationToComponent.remove(presentation)?.let { bottomContainer.remove(it) }
  }
}