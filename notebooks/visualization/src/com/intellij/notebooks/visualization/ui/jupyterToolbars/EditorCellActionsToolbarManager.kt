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
import org.intellij.lang.annotations.Language
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class EditorCellActionsToolbarManager(private val editor: EditorEx, private val cell: EditorCell): Disposable {
  private var toolbar: JupyterCellActionsToolbar? = null

  private val boundsChangeListener = object : JupyterBoundsChangeListener {
    override fun boundsChanged() = toolbar?.let {
      val targetComponent = it.targetComponent ?: return@let
      it.bounds = calculateToolbarBounds(targetComponent, it)
    }
  }

  init {
    JupyterBoundsChangeHandler.Companion.get(editor).subscribe(this, boundsChangeListener)
  }

  fun showToolbar(targetComponent: JComponent) {
    val actionGroup = getActionGroup(cell.interval.type) ?: return
    removeToolbar()

    toolbar = JupyterCellActionsToolbar(actionGroup, targetComponent)
    editor.contentComponent.add(toolbar, 0)
    updateToolbarPosition(targetComponent)
    refreshUI()
  }

  private fun updateToolbarPosition(targetComponent: JComponent) {
    toolbar?.let { toolbar ->
      toolbar.bounds = calculateToolbarBounds(targetComponent, toolbar)
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
    val toolbarHeight = toolbar.preferredSize.height
    val toolbarWidth = toolbar.preferredSize.width

    val panelHeight = panel.height
    val panelWidth = panel.width

    val delimiterSize = when(cell.interval.ordinal) {
      0 -> editor.notebookAppearance.aboveFirstCellDelimiterHeight
      else -> editor.notebookAppearance.distanceBetweenCells
    }

    val panelRoofHeight = panelHeight - delimiterSize

    val relativeOffsetRatio = when(cell.interval.ordinal) {
      0 -> 0.12
      else -> 0.05
    }

    val xOffset = (panelWidth - toolbarWidth - (panelWidth * relativeOffsetRatio)).toInt()
    val yOffset = panelHeight - panelRoofHeight - (toolbarHeight / 2)

    val panelLocationInEditor = SwingUtilities.convertPoint(panel, Point(0, 0), editor.contentComponent)

    val xCoordinate = panelLocationInEditor.x + xOffset
    val yCoordinate = panelLocationInEditor.y + yOffset

    return Rectangle(xCoordinate, yCoordinate, toolbarWidth, toolbarHeight)
  }

  companion object {
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