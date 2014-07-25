/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui.inspectionsTree;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class ScopesAndSeveritiesHintTable extends JBTable {
  private final static int SCOPE_COLUMN = 0;
  private final static int SEVERITY_COLUMN = 1;

  public ScopesAndSeveritiesHintTable(final LinkedHashMap<String, HighlightSeverity> scopeToAverageSeverityMap) {
    super(new MyModel(scopeToAverageSeverityMap));

    getColumnModel().getColumn(SCOPE_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setOpaque(false);
        UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, this);
        return this;
      }
    });

    getColumnModel().getColumn(SEVERITY_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(final JTable table,
                                                     final Object value,
                                                     final boolean isSelected,
                                                     final boolean hasFocus,
                                                     final int row,
                                                     final int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        final HighlightSeverity severity = (HighlightSeverity)value;
        setIcon(HighlightDisplayLevel.find(severity).getIcon());
        setText(SingleInspectionProfilePanel.renderSeverity(severity));
        setOpaque(false);
        UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, this);
        return this;
      }
    });
    setShowGrid(false);
    setRowSelectionAllowed(false);
    setColumnSelectionAllowed(false);
    setOpaque(false);

    for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
      int w = 0;
      final TableColumn column = getColumnModel().getColumn(i);
      for (int j = 0; j < getModel().getRowCount(); j++) {
        final Component component = prepareRenderer(column.getCellRenderer(), j, i);
        w = Math.max(component.getPreferredSize().width, w);
      }
      column.setPreferredWidth(w);
    }
  }

  private final static class MyModel extends AbstractTableModel {

    private final LinkedHashMap<String, HighlightSeverity> myScopeToAverageSeverityMap;
    private final List<String> myScopes;

    public MyModel(final LinkedHashMap<String, HighlightSeverity> scopeToAverageSeverityMap) {
      myScopeToAverageSeverityMap = scopeToAverageSeverityMap;
      myScopes = new ArrayList<String>(myScopeToAverageSeverityMap.keySet());
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
      switch (columnIndex) {
        case SCOPE_COLUMN: return String.class;
        case SEVERITY_COLUMN: return HighlightSeverity.class;
        default: throw new IllegalArgumentException();
      }
    }

    @Override
    public int getRowCount() {
      return myScopes.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(final int rowIndex, final int columnIndex) {
      switch (columnIndex) {
        case SCOPE_COLUMN: return rowIndex < getRowCount() - 1 ? myScopes.get(rowIndex) : "Everywhere else";
        case SEVERITY_COLUMN: return myScopeToAverageSeverityMap.get(myScopes.get(rowIndex));
        default: throw new IllegalArgumentException();
      }

    }
  }
}
