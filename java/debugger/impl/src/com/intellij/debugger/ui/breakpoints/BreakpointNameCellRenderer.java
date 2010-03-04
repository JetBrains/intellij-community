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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * @author Jeka
 */
public class BreakpointNameCellRenderer extends DefaultTableCellRenderer {
  private final Color myAnyExceptionForeground = new Color(128, 0, 0);

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    setBorder(null);
    BreakpointTableModel tableModel = (BreakpointTableModel)table.getModel();
    Breakpoint breakpoint = tableModel.getBreakpoint(row);
    if (breakpoint == null){
      return this;
    };
    final Icon icon = (breakpoint instanceof BreakpointWithHighlighter)?
                      breakpoint.ENABLED? ((BreakpointWithHighlighter)breakpoint).getSetIcon(false) : ((BreakpointWithHighlighter)breakpoint).getDisabledIcon(
                        false) : breakpoint.getIcon();
    setIcon(icon);
    setDisabledIcon(icon);

    if(isSelected){
      setForeground(UIUtil.getTableSelectionForeground());
    }
    else{
      Color foreColor;
      if(breakpoint instanceof AnyExceptionBreakpoint){
        foreColor = myAnyExceptionForeground;
      }
      else{
        foreColor = UIUtil.getTableForeground();
      }
      setForeground(foreColor);
    }
    setEnabled(breakpoint.ENABLED);
    return this;
  }
}
