package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import java.awt.Graphics
import java.awt.Rectangle

interface NotebookCellGutterController {
  /**
   * The method may traverse iterator without returning to the initial position, the iterator is disposable.
   *
   * @param intervalIterator Iterator pointing on the cell for which gutter should be rendered.
   * @param visualLineStart Render only part starting from the specified visual line.
   * @param visualLineEnd Render only part until the specified visual line inclusive.
   * @param logicalLineStart Corresponds to [visualLineStart].
   * @param logicalLineEnd Corresponds to [visualLineEnd].
   */
  fun paint(
    editor: EditorImpl,
    g: Graphics,
    r: Rectangle,
    intervalIterator: ListIterator<NotebookCellLines.Interval>,
    visualLineStart: Int,
    visualLineEnd: Int,
    logicalLineStart: Int,
    logicalLineEnd: Int
  )

  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName.create<NotebookCellGutterController>("org.jetbrains.plugins.notebooks.notebookCellGutterController")
  }
}