package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

public class SimpleColoredRenderer extends SimpleColoredComponent {
  private TableCellState myCellState = new TableCellState();

  public void acquireState(JTable table, boolean isSelected, boolean hasFocus, int row, int column) {
    myCellState.collectState(table, isSelected, hasFocus, row, column);
  }

  public void append(String fragment, SimpleTextAttributes attributes) {
    super.append(fragment, myCellState.modifyAttributes(attributes));
  }

  public TableCellState getCellState() {
    return myCellState;
  }

  public void setCellState(TableCellState cellState) {
    myCellState = cellState;
  }

  protected void paintComponent(Graphics g) {
    g.setColor(getBackground());
    g.fillRect(0, 0, getWidth(), getHeight());
    super.paintComponent(g);
  }
}
