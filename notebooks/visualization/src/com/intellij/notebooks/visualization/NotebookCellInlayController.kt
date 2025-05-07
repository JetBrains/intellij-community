package com.intellij.notebooks.visualization

import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.EditorCellView
import com.intellij.notebooks.visualization.ui.EditorCellViewComponent
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

interface NotebookCellInlayController {
  interface Factory {
    /**
     * There must be at most one controller (and one inlay) of some factory attached to some cell.
     *
     * This method consumes all controllers attached to some cell.
     * Upon the method call, there could be more than one controller attached to the cell.
     * For instance, it happens after cell deletion.
     *
     * The method should either choose one of the attached controllers, update and return it,
     * or should create a new controller or return null if there should be no controller for the cell.
     * Inlays from all remaining controllers will be disposed of automatically.
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
      val EP_NAME: ExtensionPointName<Factory> = ExtensionPointName.create<Factory>("org.jetbrains.plugins.notebooks.notebookCellInlayController")
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

    companion object {
      @JvmField
      val EP_NAME: ExtensionPointName<InputFactory> = ExtensionPointName.create<InputFactory>("org.jetbrains.plugins.notebooks.inputFactory")
    }
  }

  val inlay: Inlay<*>

  val factory: Factory
    get() = error("It is not used keep with AIA compatibility")

  fun onViewportChange(): Unit = Unit

  /**
   * The method may traverse iterator without returning to the initial position, the iterator is disposable.
   */
  fun paintGutter(editor: EditorImpl, g: Graphics, r: Rectangle, interval: NotebookCellLines.Interval) {}

  fun createGutterRendererLineMarker(editor: EditorEx, interval: NotebookCellLines.Interval, cellView: EditorCellView) {}

  fun updateFrameVisibility(isVisible: Boolean, interval: NotebookCellLines.Interval, color: Color) {}

  fun forceChangePanelColor(color: Color? = null) {}
}