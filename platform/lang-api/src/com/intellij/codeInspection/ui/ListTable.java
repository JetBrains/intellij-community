// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;

public class ListTable extends JBTable {

    public ListTable(ListWrappingTableModel tableModel) {
        super(tableModel);
        setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
        setRowSelectionAllowed(true);
      setRowHeight(getRowHeight() + JBUIScale.scale(4));
        setDragEnabled(false);
        final JTableHeader header = getTableHeader();
        header.setReorderingAllowed(false);
        final TableCellRenderer delegate = header.getDefaultRenderer();
        final TableCellRenderer newRenderer = new TableCellRenderer() {

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                final Component component = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                // to display the table header in disabled state when the table is disabled.
                component.setEnabled(table.isEnabled());
                return component;
            }
        };
        header.setDefaultRenderer(newRenderer);
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    @Override
    public ListWrappingTableModel getModel() {
        return (ListWrappingTableModel) super.getModel();
    }

    @Override
    public void setModel(@NotNull TableModel dataModel) {
        if (!(dataModel instanceof ListWrappingTableModel)) {
            throw new IllegalArgumentException(
                    "dataModel should be of type ListWrappingTableModel");
        }
        super.setModel(dataModel);
    }

    @NotNull
    @Override
    public Component prepareRenderer(@NotNull TableCellRenderer renderer, int row,
                                     int column) {
        final Component component =
                super.prepareRenderer(renderer, row, column);
        // to properly display the table in disabled state. See also
        // sun java bugs #4841903 and #4795987
        component.setEnabled(isEnabled());
        return component;
    }
}