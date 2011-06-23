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

/**
 * @author Jeka
 * @author Konstantin Bulenkov
 */
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class BooleanTableCellRenderer extends JCheckBox implements TableCellRenderer {
  private final JPanel myPanel = new JPanel(new BorderLayout());

  public BooleanTableCellRenderer() {
    super();
    setBorderPainted(true);
    setVerticalAlignment(CENTER);
    setHorizontalAlignment(CENTER);
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSel, boolean hasFocus, int row, int column) {
    final Color bg = table.getBackground();
    final Color fg = table.getForeground();
    final Color selBg = table.getSelectionBackground();
    final Color selFg = table.getSelectionForeground();

    myPanel.setBackground(isSel ? selBg : bg);
    if (value == null) {
      return myPanel;
    }

    setForeground(isSel ? selFg : fg);
    if (isSel) super.setBackground(selBg); else setBackground(bg);

    if (value instanceof String) {
      setSelected(Boolean.parseBoolean((String)value));
    } else {
      setSelected(((Boolean)value).booleanValue());
    }

    setBorder(hasFocus ? UIUtil.getTableFocusCellHighlightBorder() : IdeBorderFactory.createEmptyBorder(1));

    setEnabled(table.isCellEditable(row, column));

    return this;
  }
}
