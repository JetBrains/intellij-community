/*
 * Copyright 2007 Bas Leijdekkers
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

import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;

public class ListTable extends JBTable {

    public ListTable(ListWrappingTableModel tableModel) {
        super(tableModel);
        setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
        setRowSelectionAllowed(true);
        setDragEnabled(false);
        getTableHeader().setReorderingAllowed(false);
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    public ListWrappingTableModel getModel() {
        return (ListWrappingTableModel) super.getModel();
    }

    public void setModel(TableModel dataModel) {
        if (!(dataModel instanceof ListWrappingTableModel)) {
            throw new IllegalArgumentException(
                    "dataModel should be of type ListWrappingTableModel");
        }
        super.setModel(dataModel);
    }

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