package com.intellij.execution.multilaunch.design

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import com.intellij.execution.multilaunch.design.actions.ManageExecutableAction
import com.intellij.execution.multilaunch.design.actions.ManageExecutableGroup
import com.intellij.execution.multilaunch.design.popups.TableSelectorPopupController
import com.intellij.execution.multilaunch.design.tooltips.TableTooltipsController
import com.intellij.icons.AllIcons
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JTable

private const val thirdColumnWidth = 60

@ApiStatus.Internal
class ExecutablesTable(
  private val project: Project,
  private val viewModel: MultiLaunchConfigurationViewModel,
  private val lifetime: Lifetime
) : TableView<ExecutableRow>(viewModel.tableModel) {
  companion object {
    const val UNKNOWN_CELL = -1
    const val NAME_COLUMN = 0
    const val CONDITION_COLUMN = 1
    const val MODE_COLUMN = 2
  }

  init {
    rowSelectionAllowed = false
    putClientProperty(RenderingUtil.PAINT_HOVERED_BACKGROUND, false)
    visibleRowCount = 18

    adjustColumns()

    installPopupSelectorsController()
    installTooltipsController()
    installCursorsController()
    installExecutableManageActions()
  }

  override fun createDefaultTableHeader() = JBTableHeader()

  private fun adjustColumns() {
    columnModel.apply {
      getColumn(NAME_COLUMN).apply {
        resizable = false
      }
      getColumn(CONDITION_COLUMN).apply {
        resizable = false
      }
      getColumn(MODE_COLUMN).apply {
        resizable = false
        maxWidth = JBUI.scale(thirdColumnWidth)
        setHeaderRenderer { _, _, _, _, _, _ ->
          JLabel(AllIcons.General.DebugDisabled)
        }
      }
    }
    installColumnAutoEdit()
  }

  private fun installPopupSelectorsController() {
    TableSelectorPopupController().install(this)
  }

  private fun installTooltipsController() {
    TableTooltipsController(lifetime.createNested()).install(this)
  }

  private fun installColumnAutoEdit() {
    TableHoverListener.DEFAULT.removeFrom(this)

    object : TableHoverListener() {
      override fun onHover(table: JTable, row: Int, column: Int) {
        if (column.isUnknownColumn || row.isUnknownRow) return
          table.editCellAt(row, column)
      }
    }.addTo(this)
  }

  private fun installCursorsController() {
    object : TableHoverListener() {
      override fun onHover(table: JTable, row: Int, column: Int) {
        if (column == -1 || row == -1) return
        table.cursor =
          // column == 0 is handled in cell editor code
          if (!column.isNameColumn && !row.isAddExecutableRow && table.getValueAt(row, column) != null) {
            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
          } else {
            Cursor.getDefaultCursor()
          }
      }
    }.addTo(this)
  }

  private fun installExecutableManageActions() {
    val handler = object : PopupHandler() {
      override fun invokePopup(comp: Component?, x: Int, y: Int) {
        val cellInfo = getCellInfo(x, y) ?: return
        val actions = ActionManager.getInstance().getAction(ManageExecutableGroup.ID) as ActionGroup
        val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, actions)
        popupMenu.setDataContext {
          ManageExecutableAction.createContext(project, viewModel, cellInfo.executionContext, cellInfo.popupMinimalBounds)
        }
        val rect = getCellRect(cellInfo.row, cellInfo.column, false)
        popupMenu.component.show(this@ExecutablesTable, x, rect.y + rect.height)
      }

      private fun getCellInfo(mouseX: Int, mouseY: Int): EditableCellContext? {
        val point = Point(mouseX, mouseY)
        val row = rowAtPoint(point)
        val column = columnAtPoint(point)

        if (!column.isNameColumn) return null
        if (row.isUnknownRow) return null
        if (row.isAddExecutableRow) return null

        val executionContext = viewModel.rows[row] ?: return null

        val bounds = getCellPopupMinimalBounds(row, column)
        val dataContext = ManageExecutableAction.createContext(project, viewModel, executionContext, bounds)

        return EditableCellContext(
          row,
          column,
          executionContext,
          bounds,
          dataContext
        )
      }
    }

    addMouseListener(handler)
  }

  private fun getCellPopupMinimalBounds(row: Int, column: Int): Rectangle {
    val cellBounds = getCellRect(row, column, false)
    val tableLocation = locationOnScreen

    val cellScreenX = tableLocation.x + cellBounds.x
    val cellScreenY = tableLocation.y + cellBounds.y + cellBounds.height

    return Rectangle(cellScreenX, cellScreenY, cellBounds.width, cellBounds.y)
  }

  private val Int.isUnknownColumn get () = this == UNKNOWN_CELL
  private val Int.isUnknownRow get () = this == UNKNOWN_CELL
  private val Int.isNameColumn get () = this == NAME_COLUMN
  private val Int.isConditionColumn get () = this == CONDITION_COLUMN
  private val Int.isModeColumn get () = this == MODE_COLUMN
  private val Int.isAddExecutableRow get () = this == rowCount - 1

  data class EditableCellContext(
    val row: Int,
    val column: Int,
    val executionContext: ExecutableRow,
    val popupMinimalBounds: Rectangle,
    val manageContext: DataContext
  )
}

