// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ui.render.RenderingUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class TableCellState {
  private boolean mySelected;
  private Color myForeground;
  private Color myBackground;
  private Font myFont;
  private Border myCellBorder;

  public void collectState(JTable table, boolean isSelected, boolean hasFocus, int row, int column) {
    clear();
    mySelected = isSelected;
    myFont = table.getFont();
    myForeground = RenderingUtil.getForeground(table, isSelected);
    myBackground = RenderingUtil.getBackground(table, isSelected);
    myCellBorder = getBorder(isSelected, hasFocus);
  }

  @Nullable
  protected Border getBorder(boolean isSelected, boolean hasFocus) {
    if (hasFocus) {
      if (isSelected) {
        Border border = UIManager.getBorder("Table.focusSelectedCellHighlightBorder");
        if (border != null) return border;
      }
      return UIManager.getBorder("Table.focusCellHighlightBorder");
    }
    return UIManager.getBorder("Table.cellNoFocusBorder");
  }

  public void updateRenderer(JComponent renderer) {
    renderer.setForeground(myForeground);
    renderer.setBackground(myBackground);
    renderer.setFont(myFont);
    renderer.setBorder(myCellBorder);
  }

  protected void clear() {
    mySelected = false;
    myForeground = null;
    myBackground = null;
    myFont = null;
    myCellBorder = null;
  }

  public SimpleTextAttributes modifyAttributes(SimpleTextAttributes attributes) {
    if (!mySelected) return attributes;
    return new SimpleTextAttributes(attributes.getStyle(), myForeground);
  }
}

