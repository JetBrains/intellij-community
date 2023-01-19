package org.jetbrains.plugins.notebooks.visualization

import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.util.asSafely
import com.intellij.util.containers.addIfNotNull
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * A list of editors and offsets inside them. Editors are ordered according to their conformity to the UI event and context,
 * and offsets represent the places in the editors closest to the place where the event happened.
 * The event and the context are extracted from the focused component, the mouse cursor position, and the caret position.
 */
val EDITORS_WITH_OFFSETS_DATA_KEY: DataKey<List<Pair<Editor, Int>>> = DataKey.create("EDITORS_WITH_OFFSETS_DATA_KEY")

private class NotebookCellLinesIntervalDataRule : GetDataRule {
  override fun getData(dataProvider: DataProvider): NotebookCellLines.Interval? =
    EDITORS_WITH_OFFSETS_DATA_KEY.getData(dataProvider)
      ?.firstOrNull { (editor, _) ->
        NotebookCellLinesProvider.get(editor.document) != null
      }
      ?.let { (editor, offset) ->
        NotebookCellLines.get(editor).intervalsIterator(editor.document.getLineNumber(offset)).takeIf { it.hasNext() }?.next()
      }
}

private class EditorsWithOffsetsDataRule : GetDataRule {
  override fun getData(dataProvider: DataProvider): List<Pair<Editor, Int>>? {
    val contextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataProvider)
    val editor = PlatformDataKeys.EDITOR.getData(dataProvider)

    val result = mutableListOf<Pair<Editor, Int>>()

    // If the focused component is the editor, it's assumed that the current cell is the cell under the caret.
    result.addIfNotNull(
      contextComponent
        ?.asSafely<EditorComponentImpl>()
        ?.editor
        ?.let { contextEditor ->
          if (NotebookCellLinesProvider.get(contextEditor.document) != null) contextEditor to contextEditor.getOffsetFromCaretImpl()
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

    return result.takeIf(List<*>::isNotEmpty)
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