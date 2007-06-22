package com.intellij.history.integration.ui.views.table;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class RevisionsTableCellRenderer extends DefaultTableCellRenderer {
  @Override
  public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);

    if (!isSelected) {
      Color c = t.getBackground();

      if (column == 0 || column == 1) {
        if (hasValueAt(t, row, 1) || hasValueAt(t, row, 2)) c = new Color(230, 230, 250);
      }
      if (column == 2 && hasValueAt(t, row, 2)) c = new Color(255, 235, 205);

      setBackground(c);
    }

    return this;
  }

  private boolean hasValueAt(JTable t, int row, int column) {
    return t.getModel().getValueAt(row, column) != null;
  }
}
