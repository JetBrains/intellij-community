package org.jetbrains.plugins.notebooks.visualization

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.util.castSafelyTo
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * Get the position of a current [NotebookCellLines.Interval]. It is calculated according to the focused component and the caret position.
 */
val DataContext.notebookCellLinesInterval: NotebookCellLines.Interval?
  get() =
    getData(NOTEBOOK_CELL_LINES_INTERVAL_DATA_KEY)
    ?: getOffsetInEditorWithComponents(this) { it.notebookCellLinesProvider != null }
      ?.let { (editor, offset) ->
        NotebookCellLines.get(editor).intervalsIterator(editor.document.getLineNumber(offset)).takeIf { it.hasNext() }?.next()
      }

inline fun getOffsetInEditorWithComponents(
  dataContext: DataContext,
  crossinline editorFilter: (Editor) -> Boolean,
): Pair<Editor, Int>? =
  // If the focused component is the editor, it's assumed that the current cell is the cell under the caret.
  dataContext
    .getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    ?.castSafelyTo<EditorComponentImpl>()
    ?.editor
    ?.getOffsetFromCaret(editorFilter)

  // Otherwise, some component inside an editor can be focused. In that case it's assumed that the current cell is the cell closest
  // to the focused component.
  ?: getOffsetInEditorByComponentHierarchy(dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT), editorFilter)

  // If the focused component is out of the notebook editor, there still can be other editors inside the required one.
  // If some of such editor is treated as the current editor, the current cell is the cell closest to the current editor.
  ?: getOffsetInEditorByComponentHierarchy(dataContext.getData(PlatformDataKeys.EDITOR)?.contentComponent, editorFilter)

  // When a user clicks on a gutter, it's the only focused component, and it doesn't connected to the editor. However, vertical offsets
  // in the gutter can be juxtaposed to the editor.
  ?: getOffsetFromGutter(dataContext, editorFilter)

  // When a user clicks on some toolbar on some menu component, it becomes the focused components. Usually, such components have an
  // assigned editor. In that case it's assumed that the current cell is the cell under the caret.
  ?: dataContext.getData(PlatformDataKeys.EDITOR)
    ?.getOffsetFromCaret(editorFilter)

/** Private API. */
@PublishedApi
internal inline fun Editor.getOffsetFromCaret(
  crossinline editorFilter: (Editor) -> Boolean,
): Pair<Editor, Int>? =
  takeIf(editorFilter)
    ?.let { notebookEditor ->
      notebookEditor to notebookEditor.caretModel.offset.coerceAtMost(notebookEditor.document.textLength - 1).coerceAtLeast(0)
    }

/** Private API. */
@PublishedApi
internal inline fun getOffsetInEditorByComponentHierarchy(
  component: Component?,
  crossinline editorFilter: (Editor) -> Boolean,
): Pair<Editor, Int>? =
  generateSequence(component, Component::getParent)
    .zipWithNext()
    .mapNotNull { (child, parent) ->
      if (parent is EditorComponentImpl && editorFilter(parent.editor)) child to parent.editor
      else null
    }
    .firstOrNull()
    ?.let { (child, editor) ->
      val point = SwingUtilities.convertPoint(child, 0, 0, editor.contentComponent)
      editor to editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
    }

/** Private API. */
@PublishedApi
internal inline fun getOffsetFromGutter(
  dataContext: DataContext,
  crossinline editorFilter: (Editor) -> Boolean,
): Pair<Editor, Int>? {
  val gutter =
    dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? EditorGutterComponentEx
    ?: return null
  val editor =
    dataContext.getData(PlatformDataKeys.EDITOR)?.takeIf(editorFilter)
    ?: return null
  val event =
    IdeEventQueue.getInstance().trueCurrentEvent as? MouseEvent
    ?: return null
  val point = SwingUtilities.convertMouseEvent(event.component, event, gutter).point
  return editor to editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
}