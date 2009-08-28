package com.intellij.ide.todo.configurable;

import javax.swing.*;
import java.awt.*;

final class TodoTypeListCellRenderer extends DefaultListCellRenderer{
  public TodoTypeListCellRenderer(){
    setHorizontalAlignment(JLabel.CENTER);
  }

  public Component getListCellRendererComponent(
    JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean cellHasFocus
  ){
    super.getListCellRendererComponent(list,null,index,isSelected,cellHasFocus);
    Icon attributes=(Icon)value;
    setIcon(attributes);
    return this;
  }
}
