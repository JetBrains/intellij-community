/*
 * Copyright 2007-2010 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.ui.table.JBTable;

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
    public void setModel(TableModel dataModel) {
        if (!(dataModel instanceof ListWrappingTableModel)) {
            throw new IllegalArgumentException(
                    "dataModel should be of type ListWrappingTableModel");
        }
        super.setModel(dataModel);
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row,
                                     int column) {
        final Component component =
                super.prepareRenderer(renderer, row, column);
        // to properly display the table in disabled state. See also
        // sun java bugs #4841903 and #4795987
        component.setEnabled(isEnabled());
        return component;
    }
}