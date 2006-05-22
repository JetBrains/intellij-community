/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import javax.swing.table.TableCellRenderer;
import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public class StripeTableCellRenderer implements TableCellRenderer {
  private final TableCellRenderer myRenderer;
  private static final double FACTOR = 0.92;

  public StripeTableCellRenderer(final TableCellRenderer renderer) {
    myRenderer = renderer;
  }


  public StripeTableCellRenderer() {
    this(null);
  }

  public static Color darken(Color color) {
    return new Color(Math.max((int)(color.getRed()  *FACTOR), 0),
                     Math.max((int)(color.getGreen()*FACTOR), 0),
                     Math.max((int)(color.getBlue() *FACTOR), 0));
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    final JComponent component = (JComponent)getRenderer(row, column).getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    if (row % 2 != 0 && !isSelected) {
      component.setBackground(darken(table.getBackground()));
    } else {
      component.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
    }
    component.setOpaque(true);
    return component;
  }

  protected TableCellRenderer getRenderer(int row, int column) {
    return myRenderer;
  }
}
