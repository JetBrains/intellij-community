package com.intellij.openapi.fileTypes.impl;

import com.intellij.util.ui.EmptyIcon;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;
import java.awt.*;

public class FileTypeRenderer extends DefaultListCellRenderer {
  private static final Icon EMPTY_ICON = new EmptyIcon(18, 18);

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    FileType type = (FileType)value;
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    final Icon icon = type.getIcon();
    if (icon != null) {
      layeredIcon.setIcon(icon, 1, (- icon.getIconWidth() + EMPTY_ICON.getIconWidth())/2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight())/2);
    }

    setIcon(layeredIcon);
    setText(type.getDescription() + " (" + type.getName() + ")");
    return this;
  }

  public Dimension getPreferredSize() {
    return new Dimension(0, 20);
  }
}
