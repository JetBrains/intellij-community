package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.asSafely

abstract class CellLinesActionBase : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = event.dataContext.getAnyEditor()
      ?.takeIf { NotebookCellLines.hasSupport(it) } != null
  }

  open fun isModifyingSourceCode(): Boolean = true

  abstract fun actionPerformed(event: AnActionEvent,
                               editor: EditorImpl,
                               cellLines: NotebookCellLines)

  final override fun actionPerformed(event: AnActionEvent) {
    if (isModifyingSourceCode() && !isEditingAllowed(event)) return
    val editor = event.dataContext.getAnyEditor() ?: return
    val cellLines = NotebookCellLines.get(editor)
    actionPerformed(event, editor, cellLines)
  }
}

fun DataContext.getAnyEditor(): EditorImpl? =
  getData(EDITORS_WITH_OFFSETS_DATA_KEY)
    ?.firstOrNull()
    ?.first
    ?.asSafely<EditorImpl>()

fun isEditingAllowed(e: AnActionEvent): Boolean {
  val virtualFile = e.dataContext.getData(PlatformCoreDataKeys.FILE_EDITOR)?.file ?: return false
  val project = e.dataContext.getData(PlatformCoreDataKeys.PROJECT) ?: return false
  return NonProjectFileWritingAccessProvider.isWriteAccessAllowed(virtualFile, project)
}