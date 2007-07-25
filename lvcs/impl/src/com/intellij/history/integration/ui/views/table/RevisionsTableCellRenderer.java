package com.intellij.history.integration.ui.views.table;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.PutSystemLabelChange;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class RevisionsTableCellRenderer extends DefaultTableCellRenderer {
  private static final Color BLUE = new Color(230, 230, 250);
  private static final Color PINK = new Color(255, 235, 205);

  @Override
  public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);

    if (!isSelected) {
      Color c = t.getBackground();

      if (isDateOrRevisionColumn(column)) {
        Color specified = getSpecifiedColorForRevision(t, row);

        if (specified != null) {
          c = specified;
        }
        else {
          if (hasTextInRevisionOrActionColumns(t, row)) c = BLUE;
        }
      }
      if (isActionColumnWithName(t, row, column)) c = PINK;

      setBackground(c);
    }

    return this;
  }

  private boolean isDateOrRevisionColumn(int column) {
    return column == 0 || column == 1;
  }

  private Color getSpecifiedColorForRevision(JTable t, int row) {
    Change c = getModel(t).getRevisionAt(row).getCauseChange();

    // todo a bit of hack... but its much more simple than doing something right...
    if (c.isSystemLabel()) {
      int color = ((PutSystemLabelChange)c).getColor();
      if (color != -1) return new Color(color);
    }
    return null;
  }

  private boolean hasTextInRevisionOrActionColumns(JTable t, int row) {
    return hasValueAt(t, row, 1) || hasValueAt(t, row, 2);
  }

  private boolean isActionColumnWithName(JTable t, int row, final int column) {
    return column == 2 && hasValueAt(t, row, 2);
  }

  private boolean hasValueAt(JTable t, int row, int column) {
    return getModel(t).getValueAt(row, column) != null;
  }

  private RevisionsTableModel getModel(JTable t) {
    return (RevisionsTableModel)t.getModel();
  }
}
