package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.columns.ColumnsListPopup
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.IconManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.icons.HoledIcon
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.Insets
import javax.swing.Icon
import kotlin.math.max

class ColumnsListAction : ColumnHeaderActionBase() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val columns = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).columnIndices
    val total = columns.size()
    val shown = columns.asIterable().count { grid.isColumnEnabled(it) }

    val presentation = e.presentation
    val anyHidden = shown < total
    presentation.icon = columnsListIcon(anyHidden)
    presentation.description =
      if (anyHidden) DataGridBundle.message("action.Console.TableResult.ColumnsList.description.hidden", shown, total)
      else templatePresentation.description
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    ColumnsListPopup.show(grid, e)
  }
}

/** Returns the toolbar icon for the current column visibility. */
fun columnsListIcon(anyColumnHidden: Boolean): Icon =
  if (anyColumnHidden) ColumnsListIcons.withHiddenColumns else ColumnsListIcons.plain

/** Loads on first use because an icon badge needs the icon manager. */
private object ColumnsListIcons {
  private val base: Icon = AllIcons.Nodes.DataColumn

  private val badged: Icon = IconManager.getInstance().withIconBadge(base, JBUI.CurrentTheme.IconBadge.INFORMATION)

  /** The plain icon, on the same canvas as the badged one. */
  val plain: Icon = onCanvas(base, 0, 0)

  /** The badge shows that the grid hides a column, so the user does not need to open the popup. */
  val withHiddenColumns: Icon = onCanvas(badged, badgeInsets().left, badgeInsets().top)

  /** How far the badge reaches past the base icon, on each side. */
  private fun badgeInsets(): Insets = if (badged is HoledIcon) badged.getExtraInsets() else JBUI.emptyInsets()

  /**
   * Puts [icon] on a symmetric canvas so menus and toolbars center the glyph.
   *
   * A badge can extend beyond the base icon. [HoledIcon] reports the extra space.
   * [glyphX] and [glyphY] locate the glyph inside [icon].
   * Both icons place the glyph at the same point, so it stays still when the badge appears.
   */
  private fun onCanvas(icon: Icon, glyphX: Int, glyphY: Int): Icon {
    val extra = badgeInsets()
    val horizontal = max(extra.left, extra.right)
    val vertical = max(extra.top, extra.bottom)
    val canvas = LayeredIcon(2)
    canvas.setIcon(EmptyIcon.create(base.iconWidth + 2 * horizontal, base.iconHeight + 2 * vertical), 0)
    canvas.setIcon(icon, 1, horizontal - glyphX, vertical - glyphY)
    return canvas
  }
}
