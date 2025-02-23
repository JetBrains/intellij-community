// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.jupyterToolbars

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeListener
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.ui.update.MergingUpdateQueue
import org.intellij.lang.annotations.Language
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import com.intellij.util.Alarm.ThreadToUse
import com.intellij.util.ui.update.Update
import com.intellij.util.ui.update.queueTracked


class EditorCellActionsToolbarManager(
  private val editor: EditorEx,
  private val cell: EditorCell,
): Disposable {
  private var toolbar: JupyterCellActionsToolbar? = null

  private val updateQueue = MergingUpdateQueue(
    "Jupyter.EditorCellActionsToolbarManager",
    100,
    true,
    editor.component,
    this,
    editor.component,
    ThreadToUse.SWING_THREAD
  ).apply {
    setRestartTimerOnAdd(true)
  }

  private val boundsChangeListener = object : JupyterBoundsChangeListener {
    override fun boundsChanged() {
      updateQueue.queueTracked(Update.create(this@EditorCellActionsToolbarManager) {
        updateToolbarPosition(toolbar?.targetComponent ?: return@create)
      })
    }
  }

  init {
    JupyterBoundsChangeHandler.Companion.get(editor).subscribe(this, boundsChangeListener)
  }

  fun showToolbar(targetComponent: JComponent) {
    if (toolbar != null) return
    val actionGroup = getActionGroup(cell.interval.type) ?: return

    toolbar = JupyterCellActionsToolbar(actionGroup, targetComponent)
    editor.contentComponent.add(toolbar, 0)
    updateToolbarPosition(targetComponent)
    refreshUI()
  }

  private fun updateToolbarPosition(targetComponent: JComponent) {
    toolbar?.let { tb ->
      tb.validate()
      tb.bounds = calculateToolbarBounds(targetComponent, tb)
    }
  }

  fun hideToolbar() {
    removeToolbar()
    refreshUI()
  }

  private fun refreshUI() {
    editor.contentComponent.revalidate()
    editor.contentComponent.repaint()
  }

  private fun removeToolbar() = toolbar?.let {
    editor.contentComponent.remove(it)
    toolbar = null
  }

  override fun dispose() {
    JupyterBoundsChangeHandler.Companion.get(editor).unsubscribe(boundsChangeListener)
    hideToolbar()
  }

  private fun getActionGroup(cellType: NotebookCellLines.CellType): ActionGroup? = when(cellType) {
    NotebookCellLines.CellType.CODE -> {
      hideDropdownIcon(ADDITIONAL_CODE_ELLIPSIS_ACTION_GROUP_ID)
      ActionManager.getInstance().getAction(ADDITIONAL_CODE_ACTION_GROUP_ID) as? ActionGroup
    }
    NotebookCellLines.CellType.MARKDOWN -> {
      hideDropdownIcon(ADDITIONAL_MARKDOWN_ELLIPSIS_ACTION_GROUP_ID)
      ActionManager.getInstance().getAction(ADDITIONAL_MARKDOWN_ACTION_GROUP_ID) as? ActionGroup
    }
    NotebookCellLines.CellType.RAW -> null
  }

  private fun hideDropdownIcon(actionGroupId: String) = (ActionManager.getInstance().getAction(actionGroupId) as ActionGroup)
    .templatePresentation
    .putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)

  private fun calculateToolbarBounds(
    panel: JComponent,
    toolbar: JPanel,
  ): Rectangle {
    // todo: maybe fuse with JupyterAboveCellToolbarManager.Companion.calculateToolbarBounds
    toolbar.doLayout()
    panel.doLayout()

    val toolbarHeight = toolbar.preferredSize.height
    val toolbarWidth = toolbar.preferredSize.width

    val panelHeight = panel.height
    val panelWidth = panel.width

    val delimiterSize = when(cell.interval.ordinal) {
      0 -> editor.notebookAppearance.aboveFirstCellDelimiterHeight
      else -> editor.notebookAppearance.distanceBetweenCells
    }

    val panelRoofHeight = panelHeight - delimiterSize

    val xOffset = (panelWidth - toolbarWidth - (panelWidth * RELATIVE_Y_OFFSET_RATIO)).toInt()
    val yOffset = panelHeight - panelRoofHeight - (toolbarHeight / 2)

    val panelLocationInEditor = SwingUtilities.convertPoint(panel, Point(0, 0), editor.contentComponent)

    val xCoordinate = panelLocationInEditor.x + xOffset
    val yCoordinate = panelLocationInEditor.y + yOffset

    return Rectangle(xCoordinate, yCoordinate, toolbarWidth, toolbarHeight)
  }

  companion object {
    private const val RELATIVE_Y_OFFSET_RATIO = 0.05

    @Language("devkit-action-id")
    private const val ADDITIONAL_CODE_ACTION_GROUP_ID = "Jupyter.AboveCodeCellAdditionalToolbar"
    @Language("devkit-action-id")
    private const val ADDITIONAL_CODE_ELLIPSIS_ACTION_GROUP_ID = "Jupyter.AboveCodeCellAdditionalToolbar.Ellipsis"
    @Language("devkit-action-id")
    private const val ADDITIONAL_MARKDOWN_ACTION_GROUP_ID = "Jupyter.AboveMarkdownCellAdditionalToolbar"
    @Language("devkit-action-id")
    private const val ADDITIONAL_MARKDOWN_ELLIPSIS_ACTION_GROUP_ID = "Jupyter.AboveMarkdownCellAdditionalToolbar.Ellipsis"
  }
}