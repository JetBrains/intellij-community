package com.intellij.notebooks.visualization.context

import com.intellij.ide.IdeEventQueue
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.NotebookCellLinesProvider
import com.intellij.notebooks.visualization.cellSelectionModel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.asSafely
import com.intellij.util.containers.addIfNotNull
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

object NotebookDataContext {
  val NOTEBOOK_CELL_LINES_INTERVAL = DataKey.create<NotebookCellLines.Interval>("NOTEBOOK_CELL_LINES_INTERVAL")

  val DataContext.notebookEditor: EditorImpl?
    get() {
      val component = getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) ?: return null
      val editor = getData(PlatformCoreDataKeys.EDITOR)
      val noteEditor = NotebookDataContextUtils.getCurrentEditor(editor, component) ?: return null
      if (NotebookDataContextUtils.hasFocusedSearchReplaceComponent(noteEditor, component))
        return null
      return noteEditor
    }

  val DataContext.selectedCellIntervals: List<NotebookCellLines.Interval>?
    get() {
      val jupyterEditor = notebookEditor ?: return null
      val selectionModel = jupyterEditor.cellSelectionModel ?: return null
      return selectionModel.selectedCells
    }


  val DataContext.selectedCellInterval: NotebookCellLines.Interval?
    get() {
      val editor = notebookEditor ?: return null
      val selectionModel = editor.cellSelectionModel ?: return null
      return selectionModel.primarySelectedCell
    }

  val DataContext.hoveredInterval: NotebookCellLines.Interval?
    get() {
      val cached = getData(NOTEBOOK_CELL_LINES_INTERVAL)
      if (cached != null)
        return cached

      val component = getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
      val editor = notebookEditor ?: return null
      val hoveredLine = NotebookDataContextUtils.getHoveredLine(editor, component) ?: return null
      return NotebookCellLines.get(editor).getCell(hoveredLine)
    }

  val DataContext.hoveredOrSelectedInterval: NotebookCellLines.Interval?
    get() = hoveredInterval ?: selectedCellInterval


  private fun getEditorsWithOffsets(editor: Editor?, contextComponent: Component?): List<Pair<Editor, Int>> {
    // TODO Simplify. The code below is overcomplicated
    val result = mutableListOf<Pair<Editor, Int>>()

    // If the focused component is the editor, it's assumed that the current cell is the cell under the caret.
    result.addIfNotNull(
      contextComponent
        ?.asSafely<EditorComponentImpl>()
        ?.editor
        ?.let { contextEditor ->
          if (NotebookCellLinesProvider.get(contextEditor.document) != null) {
            contextEditor to contextEditor.getOffsetFromCaretImpl()
          }
          else null
        })

    // Otherwise, some component inside an editor can be focused. In that case it's assumed that the current cell is the cell closest
    // to the focused component.
    result.addIfNotNull(getOffsetInEditorByComponentHierarchy(contextComponent))

    // When a user clicks on a gutter, it's the only focused component, and it's not connected to the editor. However, vertical offsets
    // in the gutter can be juxtaposed to the editor.
    if (contextComponent is EditorGutterComponentEx && editor != null) {
      val mouseEvent = IdeEventQueue.getInstance().trueCurrentEvent as? MouseEvent
      if (mouseEvent != null) {
        val point = SwingUtilities.convertMouseEvent(mouseEvent.component, mouseEvent, contextComponent).point
        result += editor to editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
      }
    }

    // If the focused component is out of the notebook editor, there still can be other editors inside the required one.
    // If some of such editors is treated as the current editor, the current cell is the cell closest to the current editor.
    result.addIfNotNull(getOffsetInEditorByComponentHierarchy(editor?.contentComponent))

    // When a user clicks on some toolbar on some menu component, it becomes the focused components. Usually, such components have an
    // assigned editor. In that case it's assumed that the current cell is the cell under the caret.
    if (editor != null && NotebookCellLinesProvider.get(editor.document) != null) {
      result += editor to editor.getOffsetFromCaretImpl()
    }

    return result
  }

  private fun Editor.getOffsetFromCaretImpl(): Int =
    caretModel.offset.coerceAtMost(document.textLength - 1).coerceAtLeast(0)

  private fun getOffsetInEditorByComponentHierarchy(component: Component?): Pair<Editor, Int>? =
    generateSequence(component, Component::getParent)
      .zipWithNext()
      .mapNotNull { (child, parent) ->
        if (parent is EditorComponentImpl) child to parent.editor
        else null
      }
      .firstOrNull()
      ?.let { (child, editor) ->
        val point = SwingUtilities.convertPoint(child, 0, 0, editor.contentComponent)
        editor to editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
      }
}