package com.intellij.notebooks.visualization.ui

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.notebooks.ui.afterDistinctChange
import com.intellij.notebooks.ui.bind
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.notebooks.visualization.ui.EditorEmbeddedComponentLayoutManager.CustomFoldingConstraint
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.AWTEvent.MOUSE_EVENT_MASK
import java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

class CustomFoldingEditorCellViewComponent(private val cell: EditorCell, internal val component: JComponent)
  : EditorCellViewComponent() {
  private val editor: EditorEx = cell.editor

  private var foldingRegion: CustomFoldRegion? = null

  private var gutterActionRenderer: ActionToGutterRendererAdapter? = null

  private val bottomContainer = JPanel().apply {
    isOpaque = false
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
  }

  private val mainComponent = JPanel(BorderLayout()).apply {
    isOpaque = false
    add(component, BorderLayout.CENTER)
    add(bottomContainer, BorderLayout.SOUTH)
  }

  private val presentationToComponent = mutableMapOf<InlayPresentation, JComponent>()

  @TestOnly
  fun getComponentForTest(): JComponent {
    return component
  }

  private fun updateGutterIcons(gutterAction: AnAction?) {
    editor.updateManager.update { ctx ->
      gutterActionRenderer = gutterAction?.let { ActionToGutterRendererAdapter(it) }
      ctx.addFoldingOperation { modelEx ->
        foldingRegion?.update()
      }
    }
  }

  init {
    cell.gutterAction.afterDistinctChange(this) { action ->
      updateGutterIcons(action)
    }
    editor.notebookAppearance.editorBackgroundColor.bind(this) {
      bottomContainer.background = it
    }
  }

  override fun dispose(): Unit = editor.updateManager.update { ctx ->
    disposeFolding(ctx)
  }

  private fun disposeFolding(ctx: UpdateContext) {
    ctx.addFoldingOperation { foldingModel ->
      val region = foldingRegion
      foldingRegion = null
      if (region?.isValid == true) {
        foldingModel.removeFoldRegion(region)
      }
    }
    editor.componentContainer.remove(mainComponent)
  }

  override fun calculateBounds(): Rectangle {
    val region = foldingRegion ?: return mainComponent.bounds
    val location = region.location ?: return mainComponent.bounds
    return Rectangle(location.x, location.y, region.widthInPixels, region.heightInPixels)
  }

  override fun updateCellFolding(updateContext: UpdateContext) {
    updateContext.addFoldingOperation { foldingModel ->
      val prevFolding = foldingRegion
      if (prevFolding != null) {
        foldingModel.removeFoldRegion(prevFolding)
      }

      val lines = cell.interval.lines
      val newFolding = foldingModel.addCustomLinesFolding(lines.first, lines.last,
                                                          CellCustomFoldingRender(mainComponent) { gutterActionRenderer })
      if (newFolding == null) {
        error("Folding for $lines, cannot be created (e.g., due to unsupported overlapping with already existing regions.\n" +
              "Existing regions:\n ${foldingModel.allFoldRegions.joinToString(separator = "\n") { it.toString() }}")
      }
      newFolding.putUserData(CustomFoldRegion.IMMUTABLE_FOLD_REGION, true)
      foldingRegion = newFolding
      editor.componentContainer.add(mainComponent, CustomFoldingConstraint(newFolding, true))
    }
  }

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