// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.toolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.notebooks.ui.afterDistinctChange
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.NotebookVisualizationCoroutine
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedCellController
import com.intellij.notebooks.visualization.ui.DataProviderComponent
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.jupyterToolbars.JupyterCellActionsToolbar
import com.intellij.notebooks.visualization.ui.notebookEditor
import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeHandler
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import org.intellij.lang.annotations.Language
import java.awt.Point
import java.awt.Rectangle
import java.time.Duration
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.time.toKotlinDuration

/** Position of the floating toolbar in cells top right corner. */
@OptIn(FlowPreview::class) // For 'debounce'.
internal class EditorCellActionsToolbarController(
  private val cell: EditorCell,
) : SelfManagedCellController {
  private val editor = cell.editor
  private var toolbar: JupyterCellActionsToolbar? = null
  private var showToolbarJob: Job? = null
  private val coroutineScope = NotebookVisualizationCoroutine.Utils.scope.childScope("EditorCellActionsToolbarManager").also {
    Disposer.register(this, it.asDisposable())
  }

  private val targetComponent: JComponent?
    get() = (cell.view?.controllers?.firstOrNull { it is DataProviderComponent } as? DataProviderComponent)?.retrieveDataProvider()

  init {
    coroutineScope.launch {
      JupyterBoundsChangeHandler.get(editor).eventFlow.debounce(Duration.ofMillis(200).toKotlinDuration()).collect {
        val targetComponent = toolbar?.targetComponent ?: return@collect
        withContext(Dispatchers.EDT) {
          updateToolbarPosition(targetComponent)
        }
      }
    }.cancelOnDispose(this)

    cell.isSelected.afterDistinctChange(this) {
      updateToolbarVisibility()
    }
    cell.isHovered.afterDistinctChange(this) {
      updateToolbarVisibility()
    }
    editor.notebookEditor.singleFileDiffMode.afterDistinctChange(this) {
      updateToolbarVisibility()
    }
    updateToolbarVisibility()
  }

  override fun checkAndRebuildInlays() {
    val component = targetComponent ?: return
    updateToolbarPosition(component)
  }

  private fun updateToolbarVisibility() {
    val shouldBeVisible = editor.notebookEditor.singleFileDiffMode.get().not() && (cell.isSelected.get() || cell.isHovered.get())
    if (shouldBeVisible)
      showToolbar()
    else
      hideToolbar()
  }

  fun showToolbar() {
    if (toolbar != null)
      return
    val component = targetComponent ?: return
    val actionGroup = getActionGroup(cell.interval.type) ?: return

    toolbar = JupyterCellActionsToolbar(actionGroup, component, actionsUpdatedCallback = { updateToolbarPosition(component) })
    showToolbarJob?.cancel()

    showToolbarJob = coroutineScope.launch {
      delay(SHOW_TOOLBAR_DELAY_MS)
      withContext(Dispatchers.Main) {
        updateToolbarPosition(component)
        editor.contentComponent.add(toolbar, 0)
      }
    }
  }

  private fun updateToolbarPosition(targetComponent: JComponent) {
    val toolbar = toolbar ?: return
    val newBounds = calculateToolbarBounds(targetComponent, toolbar)
    if (newBounds != toolbar.bounds) {
      toolbar.bounds = newBounds
    }
  }

  fun hideToolbar() {
    showToolbarJob?.cancel()
    showToolbarJob = null
    removeToolbar()
  }

  private fun removeToolbar() = toolbar?.let {
    editor.contentComponent.remove(it)
    toolbar = null
  }

  override fun dispose() {
    coroutineScope.cancel()
    hideToolbar()
  }

  private fun getActionGroup(cellType: NotebookCellLines.CellType): ActionGroup? = when (cellType) {
    NotebookCellLines.CellType.CODE -> {
      hideDropdownIcon(ADDITIONAL_CODE_ELLIPSIS_ACTION_GROUP_ID)
      CustomActionsSchema.getInstance().getCorrectedAction(ADDITIONAL_CODE_ACTION_GROUP_ID) as? ActionGroup
    }
    NotebookCellLines.CellType.MARKDOWN -> {
      hideDropdownIcon(ADDITIONAL_MARKDOWN_ELLIPSIS_ACTION_GROUP_ID)
      ActionManager.getInstance().getAction(ADDITIONAL_MARKDOWN_ACTION_GROUP_ID) as? ActionGroup
    }
    NotebookCellLines.CellType.RAW -> null
  }

  private fun hideDropdownIcon(actionGroupId: String) = ActionManager.getInstance().getAction(actionGroupId)
    .templatePresentation
    .putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)

  private fun calculateToolbarBounds(panel: JComponent, toolbar: JComponent): Rectangle {
    val toolbarHeight = toolbar.preferredSize.height
    val toolbarWidth = toolbar.preferredSize.width

    val panelHeight = panel.height
    val panelWidth = panel.width

    val delimiterSize = when (cell.interval.ordinal) {
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
    private const val SHOW_TOOLBAR_DELAY_MS = 35L

    private const val RELATIVE_Y_OFFSET_RATIO = 0.05

    @Language("devkit-action-id")
    private const val ADDITIONAL_CODE_ACTION_GROUP_ID = "JupyterCodeCellToolbarCustomizeActionsGroup"

    @Language("devkit-action-id")
    private const val ADDITIONAL_CODE_ELLIPSIS_ACTION_GROUP_ID = "Jupyter.AboveCodeCellAdditionalToolbar.Ellipsis"

    @Language("devkit-action-id")
    private const val ADDITIONAL_MARKDOWN_ACTION_GROUP_ID = "Jupyter.AboveMarkdownCellAdditionalToolbar"

    @Language("devkit-action-id")
    private const val ADDITIONAL_MARKDOWN_ELLIPSIS_ACTION_GROUP_ID = "Jupyter.AboveMarkdownCellAdditionalToolbar.Ellipsis"
  }
}