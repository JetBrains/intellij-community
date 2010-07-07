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

/*
 * Class BreakpointTableModel
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ItemRemovable;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BreakpointTableModel extends AbstractTableModel implements ItemRemovable {
  public static final int ENABLED_STATE = 0;
  public static final int NAME = 1;

  private java.util.List<Breakpoint> myBreakpoints = null;
  private final BreakpointManager myBreakpointManager;

  public BreakpointTableModel(final Project project) {
    myBreakpoints = new ArrayList<Breakpoint>();
    myBreakpointManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
  }

  public final void setBreakpoints(Breakpoint[] breakpoints) {
    myBreakpoints.clear();
    if (breakpoints != null) {
      ContainerUtil.addAll(myBreakpoints, breakpoints);
    }
    fireTableDataChanged();
  }

  public List<Breakpoint> getBreakpoints() {
    return Collections.unmodifiableList(myBreakpoints);
  }

  public void removeBreakpoints(Breakpoint[] breakpoints) {
    myBreakpoints.removeAll(Arrays.asList(breakpoints));
    fireTableDataChanged();
  }

  public Breakpoint getBreakpoint(int index) {
    if (index < 0 || index >= myBreakpoints.size()) return null;
    return myBreakpoints.get(index);
  }

  public boolean isBreakpointEnabled(int index) {
    return ((Boolean)getValueAt(index, ENABLED_STATE)).booleanValue();
  }

  public int getBreakpointIndex(Breakpoint breakpoint) {
    return myBreakpoints.indexOf(breakpoint);
  }

  public void insertBreakpointAt(Breakpoint breakpoint, int index) {
    myBreakpoints.add(index, breakpoint);
    fireTableRowsInserted(index, index);
  }

  public void addBreakpoint(Breakpoint breakpoint) {
    myBreakpoints.add(breakpoint);
    int row = myBreakpoints.size() - 1;
    fireTableRowsInserted(row, row);
  }

  public void removeRow(int idx) {
    if (idx >= 0 && idx < myBreakpoints.size()) {
      myBreakpoints.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }
  }

  public int getRowCount() {
    return myBreakpoints.size();
  }

  public int getColumnCount() {
    return 2;
  }

  public String getColumnName(int column) {
    switch (column) {
    case ENABLED_STATE:
      return DebuggerBundle.message("breakpoint.table.header.column.enabled");
    case NAME:
      return DebuggerBundle.message("breakpoint.table.header.column.name");
    default           :
      return "";
    }
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    Breakpoint breakpoint = (Breakpoint)myBreakpoints.get(rowIndex);
    if (columnIndex == NAME) {
      return breakpoint.getDisplayName();
    }
    if (columnIndex == ENABLED_STATE) {
      return breakpoint.ENABLED? Boolean.TRUE : Boolean.FALSE;
    }
    return null;
  }

  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (rowIndex < 0 || rowIndex >= myBreakpoints.size()) {
      return;
    }
    Breakpoint breakpoint = myBreakpoints.get(rowIndex);
/*
    if (columnIndex == NAME) {
      breakpoint.setDisplayName((aValue != null)? aValue.toString() : "");
    }
    else
*/
    if (columnIndex == ENABLED_STATE) {
      final boolean isEnabled = aValue == null || ((Boolean)aValue).booleanValue();
      final boolean valueChanged = isEnabled != breakpoint.ENABLED;
      breakpoint.ENABLED = isEnabled;
      if (valueChanged) {
        breakpoint.updateUI();
      }
    }
    fireTableRowsUpdated(rowIndex, rowIndex);
  }

  public Class getColumnClass(int columnIndex) {
    if (columnIndex == ENABLED_STATE) {
      return Boolean.class;
    }
    return super.getColumnClass(columnIndex);
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
    if (columnIndex != ENABLED_STATE) {
      return false;
    }
    final boolean isSlave = myBreakpointManager.findMasterBreakpoint(myBreakpoints.get(rowIndex)) != null;
    return !isSlave;
  }
}
