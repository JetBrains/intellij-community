// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl;

/*
  In a chain of data manipulators some behaviour is common. TableMap
  provides most of this behavour and can be subclassed by filters
  that only need to override a handful of specific methods. TableMap
  implements TableModel by routing all requests to its model, and
  TableModelListener by routing all events to its listeners. Inserting
  a TableMap which has not been subclassed into a chain of table filters
  should have no effect.

  @version 1.4 12/17/97
 * @author Philip Milne */

import org.jetbrains.annotations.ApiStatus;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

@ApiStatus.Internal
public final class TableMap extends AbstractTableModel
                      implements TableModelListener {
    TableModel model;

    public TableModel getModel() {
        return model;
    }

    public void setModel(TableModel model) {
        this.model = model;
        model.addTableModelListener(this);
    }

    // By default, implement TableModel by forwarding all messages
    // to the model.

    @Override
    public Object getValueAt(int aRow, int aColumn) {
        return model.getValueAt(aRow, aColumn);
    }

    @Override
    public void setValueAt(Object aValue, int aRow, int aColumn) {
        model.setValueAt(aValue, aRow, aColumn);
    }

    @Override
    public int getRowCount() {
        return (model == null) ? 0 : model.getRowCount();
    }

    @Override
    public int getColumnCount() {
        return (model == null) ? 0 : model.getColumnCount();
    }

    @Override
    public String getColumnName(int aColumn) {
        return model.getColumnName(aColumn);
    }

    @Override
    public Class getColumnClass(int aColumn) {
        return model.getColumnClass(aColumn);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
         return model.isCellEditable(row, column);
    }
//
// Implementation of the TableModelListener interface,
//
    // By default forward all events to all the listeners.
    @Override
    public void tableChanged(TableModelEvent e) {
        fireTableChanged(e);
    }
}
