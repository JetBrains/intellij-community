package com.intellij.notebooks.visualization.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.editor.ex.EditorEx

class EditorCellRunGutterButton(private val editor: EditorEx, private val cell: EditorCell)  {
  // PY-72142 & PY-69788 & PY-72701 - adds "Run cell" button to the gutter
  private var currentAction: AnAction = RunCellGutterAction(cell)
  init { hideRunButton()  }

  var visible: Boolean = false
    set(value) {
      if (value) {
        showRunButton()
      } else {
        hideRunButton()
      }
      field = value
    }

  fun updateGutterAction(currentStatus: ProgressStatus?) {
    val newAction = when(currentStatus) {
      ProgressStatus.RUNNING -> StopCellExecutionGutterAction()
      else -> RunCellGutterAction(cell)
    }
    if (newAction == currentAction) return
    currentAction = newAction
    if (visible) showRunButton()
  }

  fun updateIfShown() = when(visible) {
    true -> showRunButton()
    false -> Unit
  }

  private fun showRunButton() = try { cell.setGutterAction(currentAction) } catch(_: Exception) {}
  private fun hideRunButton() = cell.setGutterAction(DummyEmptyAction())

  inner class RunCellGutterAction(private val cell: EditorCell) : AnAction(AllIcons.RunConfigurations.TestState.Run), ActionRemoteBehaviorSpecification.BackendOnly {
    override fun actionPerformed(e: AnActionEvent) {
      val cellLineOffset = cell.interval.lines.first
      editor.caretModel.moveToOffset(editor.document.getLineStartOffset(cellLineOffset))
      val runCellAction = ActionManager.getInstance().getAction("NotebookRunCellAction")
      runCellAction.actionPerformed(e)
    }
  }

  inner class StopCellExecutionGutterAction: AnAction(AllIcons.Run.Stop), ActionRemoteBehaviorSpecification.BackendOnly {
    override fun actionPerformed(e: AnActionEvent) {
      val interruptKernelAction = ActionManager.getInstance().getAction("JupyterInterruptKernelAction")
      interruptKernelAction.actionPerformed(e)
    }
  }

  inner class DummyEmptyAction: AnAction(AllIcons.Empty) { override fun actionPerformed(e: AnActionEvent) { } }
}
