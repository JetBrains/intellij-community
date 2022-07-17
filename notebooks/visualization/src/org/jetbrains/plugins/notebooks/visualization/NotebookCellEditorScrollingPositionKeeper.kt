package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile

val EDITOR_SCROLLING_POSITION_KEEPER_KEY = Key.create<NotebookCellEditorScrollingPositionKeeper>("EditorScrollingPositionKeeper")

interface NotebookCellEditorScrollingPositionKeeper {
  /**
   * Attaches scrolling position to the selected cell
   */
  fun saveSelectedCellPosition()

  /**
   * Attaches scrolling position to the [targetLine] if provided otherwise to the selected cell
   */
  fun savePosition(targetLine: Int?, additionalShift: Int = 0)

  /**
   * Keeps target cell(s) on the visible part of the editor
   */
  fun adjustScrollingPosition()
}

val Editor.notebookCellEditorScrollingPositionKeeper
  get() = getUserData(EDITOR_SCROLLING_POSITION_KEEPER_KEY)

fun saveScrollingPosition(virtualFile: VirtualFile, project: Project) {
  val fileEditors = FileEditorManager.getInstance(project).getAllEditors(virtualFile)
  val editors = fileEditors.filterIsInstance<TextEditor>().map { it.editor }
  for (editor in editors) {
    editor.notebookCellEditorScrollingPositionKeeper?.saveSelectedCellPosition()
  }
}
