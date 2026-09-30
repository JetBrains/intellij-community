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

  /** Returns the toolbar icon for the current column visibility. */
  public static @NotNull Icon icon(boolean anyColumnHidden) {
    return anyColumnHidden ? Icons.WITH_HIDDEN_COLUMNS : Icons.PLAIN;
  }

  /** Loads on first use, because an icon badge needs the icon manager. */
  private static final class Icons {
    private static final Icon BASE = AllIcons.Nodes.DataColumn;

    private static final Icon BADGED =
      IconManager.getInstance().withIconBadge(BASE, JBUI.CurrentTheme.IconBadge.INFORMATION);

    /** The plain icon, on the same canvas as the badged one. */
    static final Icon PLAIN = onCanvas(BASE, 0, 0);

    /** The badge tells the user that the grid hides a column, without opening the popup. */
    static final Icon WITH_HIDDEN_COLUMNS = onCanvas(BADGED, badgeInsets().left, badgeInsets().top);

    /** How far the badge reaches past the base icon, on each side. */
    private static @NotNull Insets badgeInsets() {
      return BADGED instanceof HoledIcon holed ? holed.getExtraInsets() : JBUI.emptyInsets();
    }

    /**
     * Puts {@code icon} on a canvas that is even on both sides of the glyph, so that a menu or a toolbar
     * centers the glyph and not the room the badge needs.
     * <p>
     * A badge dot reaches past the top right corner, so {@link HoledIcon} reports a size larger than the
     * icon it wraps and leaves the glyph in a corner of it. {@code glyphX} and {@code glyphY} say where the
     * glyph sits inside {@code icon}, so that both icons put their glyph in the same place and it does not
     * move when the badge appears.
     */
    private static @NotNull Icon onCanvas(@NotNull Icon icon, int glyphX, int glyphY) {
      Insets extra = badgeInsets();
      int horizontal = Math.max(extra.left, extra.right);
      int vertical = Math.max(extra.top, extra.bottom);
      LayeredIcon canvas = new LayeredIcon(2);
      canvas.setIcon(EmptyIcon.create(BASE.getIconWidth() + 2 * horizontal, BASE.getIconHeight() + 2 * vertical), 0);
      canvas.setIcon(icon, 1, horizontal - glyphX, vertical - glyphY);
      return canvas;
    }
  }
}
