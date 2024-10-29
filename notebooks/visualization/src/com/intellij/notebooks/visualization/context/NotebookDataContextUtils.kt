package com.intellij.notebooks.visualization.context

import com.intellij.find.SearchReplaceComponent
import com.intellij.ide.IdeEventQueue
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.asSafely
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

object NotebookDataContextUtils {
  fun getHoveredLine(editor: Editor?, contextComponent: Component?): Int? {
    val noteEditor = editor as? EditorImpl ?: return null

    // When a user clicks on a gutter, it's the only focused component, and it's not connected to the editor. However, vertical offsets
    // in the gutter can be juxtaposed to the editor.
    if (contextComponent is EditorGutterComponentEx) {
      val mouseEditorOffset = getMouseEditorOffset(noteEditor, contextComponent)
      if (mouseEditorOffset != null)
        return mouseEditorOffset
    }

    if (contextComponent != null && editor.contentComponent != contextComponent) {
      val calculatedLine = getRespectiveLineNumberInEditor(noteEditor, contextComponent)
      if (calculatedLine!=null)
        return calculatedLine
    }

    if (ApplicationManager.getApplication().isUnitTestMode)
      return null
    val point = editor.contentComponent.mousePosition ?: return null
    val logicalPosition = editor.xyToLogicalPosition(point)
    return logicalPosition.line
  }

  fun getCurrentEditor(editor: Editor?, contextComponent: Component?): EditorImpl? {
    val cachedEditor = editor?.takeIf { NotebookCellLines.hasSupport(it) }
    if (cachedEditor != null) {
      return cachedEditor as? EditorImpl
    }

    val componentSequence = generateSequence(contextComponent) {
      it.parent
    }

    val noteEditor = componentSequence.firstNotNullOfOrNull { component ->
      val editorComponentImpl = component as? EditorComponentImpl ?: return@firstNotNullOfOrNull null
      val noteEditor = editorComponentImpl.editor
      if (NotebookCellLines.hasSupport(noteEditor))
        return@firstNotNullOfOrNull noteEditor

      null
    }
    return noteEditor
  }

  private fun getRespectiveLineNumberInEditor(editor: Editor, component: Component): Int? {
    val point = SwingUtilities.convertPoint(component, 0, component.height, editor.contentComponent)
    val documentLineCount = editor.document.lineCount

    if (point.y < 0)
      return null

    var prospectiveLineNumber = editor.xyToLogicalPosition(point).line
    if (prospectiveLineNumber >= documentLineCount) {
      prospectiveLineNumber = documentLineCount - 1
    }
    return prospectiveLineNumber
  }


  private fun getMouseEditorOffset(editor: Editor, contextComponent: Component?): Int? {
    val mouseEvent = IdeEventQueue.getInstance().trueCurrentEvent as? MouseEvent ?: return null
    val point = SwingUtilities.convertMouseEvent(mouseEvent.component, mouseEvent, contextComponent).point
    return editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
  }

  fun hasFocusedSearchReplaceComponent(editor: Editor, contextComponent: Component?): Boolean {
    val searchReplaceComponent = editor.headerComponent.asSafely<SearchReplaceComponent>() ?: return false
    return contextComponent === searchReplaceComponent.searchTextComponent ||
           contextComponent === searchReplaceComponent.replaceTextComponent
  }
}