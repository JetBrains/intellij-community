// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.visualization.DefaultNotebookEditorAppearanceSizes
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.ui.jupyterToolbar.JupyterCellActionsToolbar
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import org.intellij.lang.annotations.Language
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class EditorCellActionsToolbarManager(private val editor: EditorEx): Disposable {
  private var toolbar: JupyterCellActionsToolbar? = null

  fun showToolbar(targetComponent: JComponent, cellType: NotebookCellLines.CellType) {
    val actionGroup = getAdditionalActionGroup(cellType) ?: return

    if (targetComponent.width == 0 || targetComponent.height == 0) return
    if (toolbar == null) toolbar = JupyterCellActionsToolbar(actionGroup, targetComponent)

    editor.contentComponent.add(toolbar, 0)
    updateToolbarPosition(targetComponent)
    refreshUI()
  }

  private fun updateToolbarPosition(targetComponent: JComponent) {
    toolbar?.let { toolbar ->
      toolbar.bounds = calculateToolbarBounds(editor, targetComponent, toolbar)
    }
  }

  fun hideToolbar() {
    toolbar?.let {
      editor.contentComponent.remove(it)
      toolbar = null
      refreshUI()
    }
  }

  private fun refreshUI() {
    editor.contentComponent.revalidate()
    editor.contentComponent.repaint()
  }

  override fun dispose(): Unit = hideToolbar()

  private fun getAdditionalActionGroup(cellType: NotebookCellLines.CellType): ActionGroup? = when(cellType) {
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

  companion object {

    @Language("devkit-action-id")
    private const val ADDITIONAL_CODE_ACTION_GROUP_ID = "Jupyter.AboveCodeCellAdditionalToolbar"
    @Language("devkit-action-id")
    private const val ADDITIONAL_CODE_ELLIPSIS_ACTION_GROUP_ID = "Jupyter.AboveCodeCellAdditionalToolbar.Ellipsis"
    @Language("devkit-action-id")
    private const val ADDITIONAL_MARKDOWN_ACTION_GROUP_ID = "Jupyter.AboveMarkdownCellAdditionalToolbar"
    @Language("devkit-action-id")
    private const val ADDITIONAL_MARKDOWN_ELLIPSIS_ACTION_GROUP_ID = "Jupyter.AboveMarkdownCellAdditionalToolbar.Ellipsis"

    private const val X_OFFSET_RATIO = 0.92
    private val DELIMITER_SIZE = DefaultNotebookEditorAppearanceSizes.distanceBetweenCells

    private fun calculateToolbarBounds(
      editor: Editor,
      panel: JComponent,
      toolbar: JPanel,
    ): Rectangle {
      // todo: maybe fuse with JupyterAboveCellToolbarManager.Companion.calculateToolbarBounds
      val toolbarHeight = toolbar.preferredSize.height
      val toolbarWidth = toolbar.preferredSize.width

      val panelHeight = panel.height
      val panelWidth = panel.width
      val panelRoofHeight = panelHeight - DELIMITER_SIZE

      val xOffset = (panelWidth * X_OFFSET_RATIO - toolbarWidth / 2).toInt()
      val yOffset = panelHeight - panelRoofHeight - (toolbarHeight / 2)

      val panelLocationInEditor = SwingUtilities.convertPoint(panel, Point(0, 0), editor.contentComponent)

      val xCoordinate = panelLocationInEditor.x + xOffset
      val yCoordinate = panelLocationInEditor.y + yOffset

      return Rectangle(xCoordinate, yCoordinate, toolbarWidth, toolbarHeight)
    }
  }
}
