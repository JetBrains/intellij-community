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

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.Table;
import com.intellij.xdebugger.XDebuggerBundle;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: May 23, 2005
 */
public class BreakpointTable extends Table {
  public BreakpointTable(final Project project) {
    super(new BreakpointTableModel(project));
    setColumnSelectionAllowed(false);
    InputMap inputMap = getInputMap();
    ActionMap actionMap = getActionMap();
    Object o = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
    if (o == null) {
      //noinspection HardCodedStringLiteral
      o = "enable_disable";
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), o);
    }
    actionMap.put(o, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (isEditing()) {
          return;
        }
        int[] indices = getSelectedRows();
        boolean currentlyMarked = true;
        for (int i = 0; i < indices.length; i++) {
          final Boolean isMarked = (Boolean)getValueAt(indices[i], BreakpointTableModel.ENABLED_STATE);
          currentlyMarked = isMarked != null? isMarked.booleanValue() : false;
          if (!currentlyMarked) {
            break;
          }
        }
        final Boolean valueToSet = currentlyMarked ? Boolean.FALSE : Boolean.TRUE;
        for (int i = 0; i < indices.length; i++) {
          setValueAt(valueToSet, indices[i], BreakpointTableModel.ENABLED_STATE);
        }
      }
    });

    setShowGrid(false);
    setIntercellSpacing(new Dimension(0, 0));
    setTableHeader(null);
    setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    setColumnSelectionAllowed(false);

    int width = new JCheckBox().getPreferredSize().width;
    TableColumnModel columnModel = getColumnModel();

    TableColumn enabledStateColumn = columnModel.getColumn(BreakpointTableModel.ENABLED_STATE);
    enabledStateColumn.setPreferredWidth(width);
    enabledStateColumn.setMaxWidth(width);
    final Class enabledStateColumnClass = getModel().getColumnClass(BreakpointTableModel.ENABLED_STATE);
    final TableCellRenderer delegateRenderer = getDefaultRenderer(enabledStateColumnClass);
    setDefaultRenderer(enabledStateColumnClass, new DefaultTableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        final Component component = delegateRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (component instanceof JComponent) {
          ((JComponent)component).setBorder(null);
        }
        return component;
      }
    });
    columnModel.getColumn(BreakpointTableModel.NAME).setCellRenderer(new BreakpointNameCellRenderer());

    getEmptyText().setText(XDebuggerBundle.message("debugger.no.breakpoints"));
  }

  public BreakpointTableModel getModel() {
    return (BreakpointTableModel)super.getModel();
  }
  
  public void setBreakpoints(Breakpoint[] breakpoints) {
    getModel().setBreakpoints(breakpoints);
  }

  public final java.util.List<Breakpoint> getBreakpoints() {
    return getModel().getBreakpoints();
  }

  public Breakpoint[] getSelectedBreakpoints() {
    if (getRowCount() == 0) {
      return Breakpoint.EMPTY_ARRAY;
    }

    int[] rows = getSelectedRows();
    if (rows.length == 0) {
      return Breakpoint.EMPTY_ARRAY;
    }
    Breakpoint[] rv = new Breakpoint[rows.length];
    for (int idx = 0; idx < rows.length; idx++) {
      rv[idx] = getModel().getBreakpoint(rows[idx]);
    }
    return rv;
  }
}
