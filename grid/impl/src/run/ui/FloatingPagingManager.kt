package com.intellij.database.run.ui

import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridUtil.getSettings
import com.intellij.database.run.ui.TableResultPanel.LayeredPaneWithSizer
import com.intellij.database.settings.DataGridSettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.ui.ClientProperty
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Popup.toolbarPanelColor
import java.awt.*
import javax.swing.JLayeredPane

class FloatingPagingManager {
  enum class AdjustmentResult {
    NOT_AFFECTED,
    HIDDEN,
    ADJUSTED
  }

  companion object {
    @JvmField
    val AVAILABLE_FOR_GRID_TYPE = Key.create<Boolean>("FloatingPaging.AvailableForGridType")
    val PRESENT_IN_GRID = Key.create<Boolean>("FloatingPaging.PresentInGrid")

    @JvmStatic
    fun shouldBePresent(grid: DataGrid): Boolean {
      val settings = getSettings(grid)
      return grid.getUserData(AVAILABLE_FOR_GRID_TYPE) == true &&
             settings?.pagingDisplayMode != DataGridSettings.PagingDisplayMode.DATA_EDITOR_TOOLBAR
    }

    @JvmStatic
    fun isPresent(grid: DataGrid): Boolean {
      return grid.getUserData(PRESENT_IN_GRID) == true
    }

    @JvmStatic
    fun installOn(grid: DataGrid, targetPane: LayeredPaneWithSizer) {
      val actionManager = ActionManager.getInstance()
      val actions = actionManager.getAction("Console.TableResult.Pagination.Floating.Group") as ActionGroup
      val moreActions = actionManager.getAction("Console.TableResult.Pagination.Floating.MoreGroup") as ActionGroup
      val toolbar = actionManager.createActionToolbar(ActionPlaces.GRID_FLOATING_PAGING_TOOLBAR, DefaultActionGroup(
        actions,
        Separator.getInstance(),
        MoreActionGroup().apply { addAll(moreActions) }
      ), true)
      toolbar.targetComponent = targetPane
      toolbar.component.isOpaque = true

      val toolbarComponent = FloatingToolbarPanel(toolbar)
      val position = getSettings(grid)?.pagingDisplayMode ?: DataGridSettings.PagingDisplayMode.GRID_CENTER_FLOATING

      targetPane.add(toolbarComponent)
      targetPane.setLayer(toolbarComponent, JLayeredPane.PALETTE_LAYER)
      ClientProperty.put(toolbarComponent, LayeredPaneWithSizer.SIZER) { pane ->
        val prefSize = toolbarComponent.preferredSize
        toolbarComponent.size = prefSize
        val containerSize = pane.size
        val y = containerSize.height - prefSize.height - JBUI.scale(12)
        val x = when (position) {
          DataGridSettings.PagingDisplayMode.DATA_EDITOR_TOOLBAR ->
            throw IllegalStateException("Floating paging should not be drawn on grid if DATA_EDITOR_TOOLBAR mode is selected")
          DataGridSettings.PagingDisplayMode.GRID_CENTER_FLOATING ->
            (containerSize.width - prefSize.width) / 2
          DataGridSettings.PagingDisplayMode.GRID_LEFT_FLOATING ->
            JBUI.scale(12)
          DataGridSettings.PagingDisplayMode.GRID_RIGHT_FLOATING ->
            containerSize.width - prefSize.width - JBUI.scale(22)
        }
        toolbarComponent.setLocation(x, y)
      }
      grid.putUserData(PRESENT_IN_GRID, true)
    }

    @JvmStatic
    fun uninstallFrom(grid: DataGrid, targetPane: LayeredPaneWithSizer) {
      targetPane.components.filterIsInstance<FloatingToolbarPanel>().forEach { targetPane.remove(it) }
      grid.removeUserData(PRESENT_IN_GRID)
    }

    @JvmStatic
    fun adjustAction(e: AnActionEvent): AdjustmentResult {
      val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY)
      if (grid == null) {
        return AdjustmentResult.NOT_AFFECTED
      }

      if (ActionPlaces.EDITOR_TOOLBAR == e.place) {
        if (isPresent(grid)) {
          e.presentation.isEnabledAndVisible = false
          return AdjustmentResult.HIDDEN
        }
        return AdjustmentResult.NOT_AFFECTED
      }
      else {
        e.presentation.putClientProperty(ActionUtil.USE_SMALL_FONT_IN_TOOLBAR, true)
        return AdjustmentResult.ADJUSTED
      }
    }

    class FloatingToolbarPanel(toolbar: ActionToolbar) : RoundedCornersJBPanel() {
      init {
        toolbar.minimumButtonSize = JBUI.size(JBUI.scale(22))
        toolbar.component.border = JBUI.Borders.empty(JBUI.scale(6))
        toolbar.component.isOpaque = false
        add(toolbar.component, BorderLayout.CENTER)
      }

      override fun getPreferredSize(): Dimension {
        return super.getPreferredSize()
      }
    }

    open class RoundedCornersJBPanel : JBPanel<RoundedCornersJBPanel>(BorderLayout()) {
      init {
        isOpaque = false
      }

      override fun paintComponent(g: Graphics) {
        g.color = toolbarPanelColor()
        val config = GraphicsUtil.setupAAPainting(g)
        val cornerRadius = JBUI.scale(16)
        g.fillRoundRect(0, 0, width - 1, height - 1 , cornerRadius, cornerRadius)
        g.color = JBColor(Gray._235, Gray._64)
        g.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)
        config.restore()
      }
    }
  }
}