package org.jetbrains.plugins.notebooks.visualization.outputs.impl

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.notebooks.visualization.*
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputInlayController
import java.awt.event.MouseEvent

internal class NotebookOutputCollapseAllAction private constructor() : ToggleAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.notebookEditor != null
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    !allCollapsingComponents(e).any { it.isSeen }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    markScrollingPositionBeforeOutputCollapseToggle(e)

    for (component in allCollapsingComponents(e)) {
      component.isSeen = !state
    }
  }

  private fun allCollapsingComponents(e: AnActionEvent): Sequence<CollapsingComponent> {
    val inlayManager = e.notebookCellInlayManager ?: return emptySequence()
    return getCollapsingComponents(inlayManager.editor, NotebookCellLines.get(inlayManager.editor).intervals)
  }
}

// same as Collapse All Action, but collapse outputs of selected cells
internal class NotebookOutputCollapseAllInSelectedCellsAction private constructor() : ToggleAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    super.update(e)
    val editor = e.notebookEditor
    e.presentation.isEnabled = editor != null
    e.presentation.isVisible = editor?.cellSelectionModel?.let { it.selectedCells.size > 1 } ?: false
   }

  override fun isSelected(e: AnActionEvent): Boolean =
    !getSelectedCollapsingComponents(e).any { it.isSeen }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    markScrollingPositionBeforeOutputCollapseToggle(e)

    for(component in getSelectedCollapsingComponents(e)) {
      component.isSeen = !state
    }
  }

  private fun getSelectedCollapsingComponents(e: AnActionEvent): Sequence<CollapsingComponent> {
    val inlayManager = e.notebookCellInlayManager ?: return emptySequence()
    val selectedCells = inlayManager.editor.cellSelectionModel?.selectedCells ?: return emptySequence()
    return getCollapsingComponents(inlayManager.editor, selectedCells)
  }
}

internal class NotebookOutputCollapseAllInCellAction private constructor() : ToggleAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = getCollapsingComponents(e) != null
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val collapsingComponents = getCollapsingComponents(e) ?: return false
    return collapsingComponents.isNotEmpty() && collapsingComponents.all { !it.isSeen }
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    markScrollingPositionBeforeOutputCollapseToggle(e)

    getCollapsingComponents(e)?.forEach {
      it.isSeen = !state
    }
  }
}

internal class NotebookOutputCollapseSingleInCellAction private constructor() : ToggleAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = getCollapsingComponents(e) != null
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    getExpectedComponent(e)?.isSeen?.let { !it } ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    markScrollingPositionBeforeOutputCollapseToggle(e)
    getExpectedComponent(e)?.isSeen = !state
  }

  private fun getExpectedComponent(e: AnActionEvent): CollapsingComponent? =
    e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
      ?.castSafelyTo<CollapsingComponent>()
      ?.let { expectedComponent ->
        getCollapsingComponents(e)?.singleOrNull { it === expectedComponent }
      }
}

private fun getCollapsingComponents(e: AnActionEvent): List<CollapsingComponent>? {
  val interval = e.dataContext.notebookCellLinesInterval ?: return null
  return e.getData(PlatformDataKeys.EDITOR)?.let { getCollapsingComponents(it, interval) }
}

private fun getCollapsingComponents(editor: Editor, interval: NotebookCellLines.Interval): List<CollapsingComponent>? =
  NotebookCellInlayManager.get(editor)
    ?.inlaysForInterval(interval)
    ?.filterIsInstance<NotebookOutputInlayController>()
    ?.firstOrNull()
    ?.collapsingComponents

private fun getCollapsingComponents(editor: Editor, intervals: Iterable<NotebookCellLines.Interval>): Sequence<CollapsingComponent> =
  intervals.asSequence()
    .filter { it.type == NotebookCellLines.CellType.CODE }
    .mapNotNull { getCollapsingComponents(editor, it) }
    .flatMap { it }

private val AnActionEvent.notebookCellInlayManager: NotebookCellInlayManager?
  get() = getData(PlatformDataKeys.EDITOR)?.let(NotebookCellInlayManager.Companion::get)

private val AnActionEvent.notebookEditor: EditorImpl?
  get() = notebookCellInlayManager?.editor

private fun markScrollingPositionBeforeOutputCollapseToggle(e: AnActionEvent) {
  val cell = e.dataContext.notebookCellLinesInterval ?: return
  val editor = e.notebookCellInlayManager?.editor ?: return
  val notebookCellEditorScrollingPositionKeeper = editor.notebookCellEditorScrollingPositionKeeper ?: return

  val outputsCellVisible = isLineVisible(editor, cell.lines.last)
  if (!outputsCellVisible) {
    val cellOutputInlays = editor.inlayModel.getBlockElementsInRange(editor.document.getLineEndOffset(cell.lines.last), editor.document.getLineEndOffset(cell.lines.last))
    val visibleArea = editor.scrollingModel.visibleAreaOnScrollingFinished

    for (i in (cellOutputInlays.size - 1) downTo 1) {
      val inlay = cellOutputInlays[i]
      val bounds = inlay.bounds ?: continue
      val outputTopIsAboveScreen = bounds.y < visibleArea.y
      val outputBottomIsOnOrBelowScreen = bounds.y + bounds.height > visibleArea.y
      if (outputTopIsAboveScreen) {
        if ((outputBottomIsOnOrBelowScreen)) {
          val inputEvent = e.inputEvent
          val additionalShift: Int
          if (inputEvent is MouseEvent) {
            // Adjust scrolling so, that the collapsed output is under the mouse pointer
            additionalShift = inputEvent.y - bounds.y - editor.lineHeight
          } else {
            // Adjust scrolling so, that the collapsed output is visible on the screen
            additionalShift = visibleArea.y - bounds.y + editor.lineHeight
          }

          notebookCellEditorScrollingPositionKeeper.savePosition(cell.lines.last, additionalShift)
          return
        }
        else {
          val topVisibleLine: Int = editor.xyToLogicalPosition(visibleArea.location).line
          notebookCellEditorScrollingPositionKeeper.savePosition(topVisibleLine)
          return
        }
      }
    }
  }
  notebookCellEditorScrollingPositionKeeper.savePosition(cell.lines.first)
}