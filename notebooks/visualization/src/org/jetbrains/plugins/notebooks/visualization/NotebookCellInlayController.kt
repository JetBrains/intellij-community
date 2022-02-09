package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import java.awt.Graphics
import java.awt.Rectangle

interface NotebookCellInlayController {
  interface Factory {
    /**
     * There must be at most one controller (and one inlay) of some factory attached to some cell.
     *
     * This methods consumes all controllers attached to some cell. Upon the method call,
     * there could be more than one controller attached to the cell.
     * For instance, it happens after cell deletion.
     *
     * The method should either choose one of the attached controllers, update and return it,
     * or should create a new controller, or return null if there should be no controller for the cell.
     * Inlays from all remaining controllers will be disposed automatically.
     *
     * The method may traverse iterator without returning to the initial position, the iterator is disposable.
     */
    fun compute(
      editor: EditorImpl,
      currentControllers: Collection<NotebookCellInlayController>,
      intervalIterator: ListIterator<NotebookCellLines.Interval>
    ): NotebookCellInlayController?

    companion object {
      @JvmField
      val EP_NAME = ExtensionPointName.create<Factory>("org.jetbrains.plugins.notebooks.notebookCellInlayController")
    }
  }

  val inlay: Inlay<*>

  val factory: Factory

  fun onViewportChange() {}

  /**
   * The method may traverse iterator without returning to the initial position, the iterator is disposable.
   */
  fun paintGutter(editor: EditorImpl,
                  g: Graphics,
                  r: Rectangle,
                  intervalIterator: ListIterator<NotebookCellLines.Interval>)
}