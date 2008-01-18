package com.intellij.ide.todo.configurable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

final class TodoTypeTableCellRenderer extends DefaultTableCellRenderer{
  public TodoTypeTableCellRenderer(){
    setHorizontalAlignment(JLabel.CENTER);
  }

  public Component getTableCellRendererComponent(
    JTable table,
    Object value,
    boolean isSelected,
    boolean hasFocus,
    int row,
    int column
  ){
    super.getTableCellRendererComponent(table,null,isSelected,hasFocus,row,column);
    Icon attributes=(Icon)value;
    setIcon(attributes);
    return this;
  }
}
