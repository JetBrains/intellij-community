package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import com.intellij.util.asSafely
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

class ControllerEditorCellViewComponent(
  internal val controller: NotebookCellInlayController
) : EditorCellViewComponent {

  private val cellEventListeners = EventDispatcher.create(EditorCellViewComponentListener::class.java)

  override val location: Point
    get() {
      val component = controller.inlay.renderer as JComponent
      return component.location
    }

  override val size: Dimension
    get() {
      val component = controller.inlay.renderer as JComponent
      return component.size
    }

  private val listener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      cellEventListeners.multicaster.componentBoundaryChanged(e.component.location, e.component.size)
    }
  }

  init {
    controller.inlay.renderer.asSafely<JComponent>()?.addComponentListener(listener)
  }

  override fun updateGutterIcons(gutterAction: AnAction?) {
    val inlay = controller.inlay
    inlay.putUserData(NotebookCellInlayController.gutterActionKey, gutterAction)
    inlay.update()
  }

  override fun dispose() {
    controller.inlay.renderer.asSafely<JComponent>()?.removeComponentListener(listener)
    controller.let { controller -> Disposer.dispose(controller.inlay) }
  }

  override fun onViewportChange() {
    controller.onViewportChange()
  }


  override fun addViewComponentListener(listener: EditorCellViewComponentListener) {
    cellEventListeners.addListener(listener)
  }

  override fun updatePositions() {
    cellEventListeners.multicaster.componentBoundaryChanged(location, size)
  }
}