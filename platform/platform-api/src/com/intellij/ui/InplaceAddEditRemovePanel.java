// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public abstract class InplaceAddEditRemovePanel<T> extends AddEditRemovePanel<T> {
  public InplaceAddEditRemovePanel(TableModel<T> model, List<T> data) {
    super(model, data);
  }

  public InplaceAddEditRemovePanel(TableModel<T> model, List<T> data, @Nullable @NlsContexts.Label String label) {
    super(model, data, label);
  }

  @Override
  protected void doAdd() {
    super.doAdd();
    JBTable table = getTable();
    int selected = table.getSelectedRow();
    if (selected != -1) {
      table.editCellAt(selected, 0);
    }
  }

  @Override
  protected void doEdit() {
    JBTable table = getTable();
    if (!table.isEditing()) {
      int selectedRow = table.getSelectedRow();
      int selectedColumn = table.getSelectedColumn();
      if (selectedRow != -1 && selectedColumn != -1) {
        table.editCellAt(selectedRow, selectedColumn);
      }
    }
  }

  @Override
  protected @Nullable T editItem(T o) {
    return o;
  }
}
