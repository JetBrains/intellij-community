package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.columns.ColumnsListPopup;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.ui.IconManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.icons.HoledIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.Insets;

public class ColumnsListAction extends ColumnHeaderActionBase {
  @Override
  protected void update(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    int total = 0;
    int shown = 0;
    for (ModelIndex<GridColumn> columnIdx : grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumnIndices().asIterable()) {
      total++;
      if (grid.isColumnEnabled(columnIdx)) shown++;
    }

    Presentation presentation = e.getPresentation();
    boolean anyHidden = shown < total;
    presentation.setIcon(icon(anyHidden));
    presentation.setDescription(anyHidden
                                ? DataGridBundle.message("action.Console.TableResult.ColumnsList.description.hidden", shown, total)
                                : getTemplatePresentation().getDescription());
  }

  @Override
  protected void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    ColumnsListPopup.show(grid, e);
  }

  /**
   * The toolbar icon, which carries a badge while the grid hides a column.
   * <p>
   * The badge comes from the icon manager of the running IDE. A test application installs a manager that
   * returns the icon unchanged, so a test compares against this method rather than against the badge.
   */
  public static @NotNull Icon icon(boolean anyColumnHidden) {
    return anyColumnHidden ? Icons.WITH_HIDDEN_COLUMNS : Icons.PLAIN;
  }

  /** Loads on first use, because an icon badge needs the icon manager. */
  private static final class Icons {
    private static final Icon BASE = AllIcons.Nodes.DataColumn;

    /** The badge tells the user that the grid hides a column, without opening the popup. */
    static final Icon WITH_HIDDEN_COLUMNS =
      IconManager.getInstance().withIconBadge(BASE, JBUI.CurrentTheme.IconBadge.INFORMATION);

    /**
     * The plain icon, padded to the bounds of the badged one.
     * <p>
     * A badge dot protrudes past the base icon, so {@link HoledIcon} reports a larger size than the icon it
     * wraps. A toolbar centers whatever icon it is given, so without this padding the glyph moves as soon as
     * the badge appears.
     */
    static final Icon PLAIN = paddedToBadgedBounds();

    private static @NotNull Icon paddedToBadgedBounds() {
      int width = WITH_HIDDEN_COLUMNS.getIconWidth();
      int height = WITH_HIDDEN_COLUMNS.getIconHeight();
      if (width == BASE.getIconWidth() && height == BASE.getIconHeight()) {
        return BASE;
      }
      Insets extra = WITH_HIDDEN_COLUMNS instanceof HoledIcon holed ? holed.getExtraInsets() : JBUI.emptyInsets();
      LayeredIcon padded = new LayeredIcon(2);
      padded.setIcon(EmptyIcon.create(width, height), 0);
      padded.setIcon(BASE, 1, extra.left, extra.top);
      return padded;
    }
  }
}
