// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui.inspectionsTree;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
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
public final class ScopesAndSeveritiesHintTable extends JBTable {
  private static final int SCOPE_COLUMN = 0;
  private static final int SEVERITY_COLUMN = 1;

  public ScopesAndSeveritiesHintTable(final LinkedHashMap<String, HighlightDisplayLevel> scopeToAverageSeverityMap, String defaultScopeName) {
    super(new MyModel(scopeToAverageSeverityMap, defaultScopeName));

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
        final HighlightDisplayLevel level = (HighlightDisplayLevel)value;
        setIcon(level.getIcon());
        setText(SingleInspectionProfilePanel.renderSeverity(level.getSeverity()));
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
      column.setPreferredWidth(w + 1);
    }
  }

  private static final class MyModel extends AbstractTableModel {

    private final LinkedHashMap<String, HighlightDisplayLevel> myScopeToAverageSeverityMap;
    private final String myDefaultScopeName;
    private final List<String> myScopes;

    MyModel(final LinkedHashMap<String, HighlightDisplayLevel> scopeToAverageSeverityMap, String defaultScopeName) {
      myScopeToAverageSeverityMap = scopeToAverageSeverityMap;
      myDefaultScopeName = defaultScopeName;
      myScopes = new ArrayList<>(myScopeToAverageSeverityMap.keySet());
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
      return switch (columnIndex) {
        case SCOPE_COLUMN -> String.class;
        case SEVERITY_COLUMN -> HighlightDisplayLevel.class;
        default -> throw new IllegalArgumentException();
      };
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
      final String scopeName = myScopes.get(rowIndex);
      return switch (columnIndex) {
        case SCOPE_COLUMN -> myDefaultScopeName.equals(scopeName) ? "Everywhere else" : scopeName;
        case SEVERITY_COLUMN -> myScopeToAverageSeverityMap.get(scopeName);
        default -> throw new IllegalArgumentException();
      };

    }
  }
}
