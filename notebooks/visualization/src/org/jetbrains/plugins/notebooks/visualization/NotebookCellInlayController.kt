package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCell
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCellView
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCellViewComponent
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
      intervalIterator: ListIterator<NotebookCellLines.Interval>,
    ): NotebookCellInlayController?

    companion object {
      @JvmField
      val EP_NAME = ExtensionPointName.create<Factory>("org.jetbrains.plugins.notebooks.notebookCellInlayController")
    }
  }

  abstract class LazyFactory : Factory {
    internal val cellOrdinalsInCreationBlock = hashSetOf<Int>()

    abstract fun isAvailable(editor: EditorImpl): Boolean

    abstract fun getControllerClass(): Class<out NotebookCellInlayController>

    abstract fun getOldController(editor: EditorImpl, currentControllers: Collection<NotebookCellInlayController>, interval: NotebookCellLines.Interval): NotebookCellInlayController?

    abstract fun getNewController(editor: EditorImpl, interval: NotebookCellLines.Interval): NotebookCellInlayController

    override fun compute(editor: EditorImpl, currentControllers: Collection<NotebookCellInlayController>, intervalIterator: ListIterator<NotebookCellLines.Interval>): NotebookCellInlayController? {
      if (!isAvailable(editor)) {
        return null
      }

      val interval = intervalIterator.next()
      val oldController = getOldController(editor, currentControllers, interval)
      if (oldController != null) {
        return oldController
      }

      if (!cellOrdinalsInCreationBlock.contains(interval.ordinal)) {
        return null
      }
      return getNewController(editor, interval)
    }
  }

  /**
   * Marker interface for factories producing custom editors for cells
   */
  interface InputFactory {

    fun createComponent(editor: EditorImpl, cell: EditorCell): EditorCellViewComponent

    fun supports(editor: EditorImpl, cell: EditorCell): Boolean

  }

  val inlay: Inlay<*>

  val factory: Factory

  fun onViewportChange() {}

  /**
   * The method may traverse iterator without returning to the initial position, the iterator is disposable.
   */
  fun paintGutter(editor: EditorImpl, g: Graphics, r: Rectangle, interval: NotebookCellLines.Interval) {}

  fun createGutterRendererLineMarker(editor: EditorEx, interval: NotebookCellLines.Interval, cellView: EditorCellView) {}

  companion object {
    val GUTTER_ACTION_KEY = Key<AnAction>("jupyter.editor.cell.gutter.action")
  }
}