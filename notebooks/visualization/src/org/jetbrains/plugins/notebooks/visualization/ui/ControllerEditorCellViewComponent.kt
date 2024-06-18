package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Disposer
import com.intellij.util.asSafely
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

class ControllerEditorCellViewComponent(
  internal val controller: NotebookCellInlayController,
  private val parent: EditorCellInput,
) : EditorCellViewComponent(), HasGutterIcon {

  private val listener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      parent.invalidate()
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

  override fun doDispose() {
    controller.inlay.renderer.asSafely<JComponent>()?.removeComponentListener(listener)
    controller.let { controller -> Disposer.dispose(controller.inlay) }
  }

  override fun doViewportChange() {
    controller.onViewportChange()
  }

  override fun calculateBounds(): Rectangle {
    val component = controller.inlay.renderer as JComponent
    return component.bounds
  }
}