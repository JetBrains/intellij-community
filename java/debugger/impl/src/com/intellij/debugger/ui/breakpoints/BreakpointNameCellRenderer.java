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
                      breakpoint.ENABLED? ((BreakpointWithHighlighter)breakpoint).getSetIcon() : ((BreakpointWithHighlighter)breakpoint).getDisabledIcon() :  
                      breakpoint.getIcon();
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
