// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.toolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.notebooks.jupyter.core.jupyter.CellType
import com.intellij.notebooks.ui.afterDistinctChange
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.NotebookVisualizationCoroutine
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedCellController
import com.intellij.notebooks.visualization.settings.NotebookSettings
import com.intellij.notebooks.visualization.ui.DataProviderComponent
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.NotebookUiUtils.intersectsEvenIfEmpty
import com.intellij.notebooks.visualization.ui.jupyterToolbars.JupyterCellActionsToolbar
import com.intellij.notebooks.visualization.ui.notebookEditor
import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeHandler
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.PlatformUtils
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import java.awt.Point
import java.awt.Rectangle
import java.time.Duration
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min
import kotlin.time.toKotlinDuration

/** Position of the floating toolbar in cells top right corner. */
internal class EditorCellActionsToolbarController(
  private val cell: EditorCell,
) : SelfManagedCellController {
  private val editor = cell.editor
  private var toolbar: JupyterCellActionsToolbar? = null
  private var showToolbarJob: Job? = null
  private val coroutineScope = NotebookVisualizationCoroutine.Utils.scope.childScope("EditorCellActionsToolbarManager").also { scope ->
    Disposer.register(this@EditorCellActionsToolbarController) {
      scope.cancel(CancellationException("Disposed EditorCellActionsToolbarController"))
    }
  }

  private val targetComponent: JComponent?
    get() = cell.view?.controllers?.filterIsInstance<DataProviderComponent>()?.firstOrNull()?.retrieveDataProvider()

  init {
    coroutineScope.launch {
      @OptIn(FlowPreview::class) // For debounce.
      JupyterBoundsChangeHandler.get(editor).eventFlow.debounce(Duration.ofMillis(200).toKotlinDuration()).collect {
        val targetComponent = toolbar?.targetComponent ?: return@collect
        withContext(Dispatchers.EDT) {
          updateToolbarPosition(targetComponent)
        }
      }
    }.cancelOnDispose(this)

    editor.scrollingModel.addVisibleAreaListener {
      if (!NotebookSettings.getInstance().cellToolbarStickyVisible) return@addVisibleAreaListener
      val targetComponent = toolbar?.targetComponent ?: return@addVisibleAreaListener
      updateToolbarPosition(targetComponent)
    }

    cell.isSelected.afterDistinctChange(this) {
      updateToolbarVisibility()
    }
    cell.isHovered.afterDistinctChange(this) {
      updateToolbarVisibility()
    }
    editor.notebookEditor.singleFileDiffMode.afterDistinctChange(this) {
      updateToolbarVisibility()
    }
    editor.notebookEditor.cellAddToolbarShown.afterDistinctChange(this) {
      updateToolbarVisibility()
    }
    updateToolbarVisibility()
  }

  /**
   * Functionality to hide "CellActions" toolbar when "AddNewCell" tollbar is shown initially was made for specifically long toolbar of DS.
   * Currently, it is disabled for all platforms except DS.
   */
  fun shouldShowCellToolbarTogetherWithAddNewCellToolbar(): Boolean {
    return !PlatformUtils.isDataSpell()
  }

  override fun checkAndRebuildInlays() {
    val component = targetComponent ?: return
    toolbar?.apply {
      background = editor.notebookAppearance.editorBackgroundColor()
    }
    updateToolbarPosition(component)
  }

  private fun updateToolbarVisibility() {
    val shouldBeVisible = editor.notebookEditor.singleFileDiffMode.get().not() &&
                          (editor.notebookEditor.cellAddToolbarShown.get().not() || shouldShowCellToolbarTogetherWithAddNewCellToolbar()) &&
                          ((cell.isSelected.get() && NotebookSettings.getInstance().showToolbarForSelectedCell) || cell.isHovered.get())
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

    toolbar = JupyterCellActionsToolbar(actionGroup, component, actionsUpdatedCallback = { updateToolbarPosition(component) }).apply {
      background = editor.notebookAppearance.editorBackgroundColor()
    }
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
    val toolbarBounds = it.bounds
    editor.contentComponent.remove(it)
    editor.contentComponent.repaint(toolbarBounds)
    toolbar = null
  }

  override fun dispose() {
    coroutineScope.cancel()
    hideToolbar()
  }

  private fun getActionGroup(cellType: CellType): ActionGroup? = when (cellType) {
    CellType.CODE -> {
      hideDropdownIcon(ADDITIONAL_CODE_ELLIPSIS_ACTION_GROUP_ID)
      CustomActionsSchema.getInstance().getCorrectedAction(ADDITIONAL_CODE_ACTION_GROUP_ID) as? ActionGroup
    }
    CellType.MARKDOWN -> {
      hideDropdownIcon(ADDITIONAL_MARKDOWN_ELLIPSIS_ACTION_GROUP_ID)
      ActionUtil.getActionGroup(ADDITIONAL_MARKDOWN_ACTION_GROUP_ID)
    }
    CellType.RAW -> null
  }

  private fun hideDropdownIcon(actionGroupId: String) = ActionUtil.getAction(actionGroupId)!!
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

    val xOffset = panelWidth - toolbarWidth - (panelWidth * RELATIVE_Y_OFFSET_RATIO).toInt()
    val yOffset = panelHeight - panelRoofHeight - (toolbarHeight / 2)

    val panelLocationInEditor = SwingUtilities.convertPoint(panel, Point(0, 0), editor.contentComponent)

    val xCoordinate = panelLocationInEditor.x + xOffset
    var yCoordinate = panelLocationInEditor.y + yOffset

    // We have StickyLinesPanel - to show current class/method. This panel has a full-editor width.
    if (NotebookSettings.getInstance().cellToolbarStickyVisible) {
      yCoordinate = max(yCoordinate, editor.contentComponent.visibleRect.y + JBUI.scale(2) + editor.stickyLinesPanelHeight)

      val bounds = cell.view?.calculateBounds()
      if (bounds != null) {
        yCoordinate = min(yCoordinate, bounds.y + bounds.height - toolbarHeight - JBUI.scale(4))
      }
    }

    val result =  Rectangle(xCoordinate, yCoordinate, toolbarWidth, toolbarHeight)

    // We have also EditorInspectionsActionToolbar in the top right editor corner, and we want to protect from overlap.
    val statusComponent = (editor.scrollPane as? JBScrollPane)?.statusComponent
    if (statusComponent != null) {
      val statusComponentLocation = SwingUtilities.convertPoint(statusComponent, Point(0, 0), editor.contentComponent)
      val statusComponentBounds = Rectangle(statusComponentLocation, statusComponent.size)

      if (result.intersectsEvenIfEmpty(statusComponentBounds)) {
        result.y = statusComponentBounds.y + statusComponentBounds.height
      }
    }

    return result
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