/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

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
    if (isSelected) {
      myForeground = table.getSelectionForeground();
      myBackground = table.getSelectionBackground();
    }
    else {
      myForeground = table.getForeground();
      myBackground = table.getBackground();
    }
    if (hasFocus) {
      myCellBorder = UIUtil.getTableFocusCellHighlightBorder();
      if (table.isCellEditable(row, column)) {
        myForeground = UIUtil.getTableFocusCellForeground();
        myBackground = UIUtil.getTableFocusCellBackground();
      }
    }
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

