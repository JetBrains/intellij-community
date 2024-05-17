package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.markup.GutterIconRenderer
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class EditorRunCellGutterIconRenderer(private val lines: IntRange) : GutterIconRenderer() {
  // PY-72142 & PY-69788 & PY-72701 - adds "Run cell" button to the gutter

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as EditorRunCellGutterIconRenderer
    return lines == other.lines
  }

  override fun getClickAction(): AnAction = CellRunAction(lines, action)
  override fun getTooltipText(): String?  = action.templateText
  override fun hashCode(): Int = lines.hashCode()
  override fun isNavigateAction(): Boolean = true
  override fun getIcon(): Icon = AllIcons.RunConfigurations.TestState.Run

  private class CellRunAction(private val lines: IntRange,
                              private val action: AnAction) : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
      val editor = e.getData(CommonDataKeys.EDITOR)
      editor?.caretModel?.moveToOffset(editor.document.getLineStartOffset(lines.first))
      action.actionPerformed(e)
    }
  }

  companion object {
    private const val RUN_CELL_ACTION_ID = "NotebookRunCellAction"
    private val action = ActionManager.getInstance().getAction(RUN_CELL_ACTION_ID)
  }

}
