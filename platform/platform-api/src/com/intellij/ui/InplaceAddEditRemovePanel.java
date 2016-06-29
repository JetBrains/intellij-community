/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public abstract class InplaceAddEditRemovePanel<T> extends AddEditRemovePanel<T> {
  public InplaceAddEditRemovePanel(TableModel<T> model, List<T> data) {
    super(model, data);
  }

  public InplaceAddEditRemovePanel(TableModel<T> model, List<T> data, @Nullable String label) {
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
      int selected = table.getSelectedRow();
      if (selected != -1) {
        table.editCellAt(selected, 0);
      }
    }
  }
  
  @Nullable
  @Override
  protected T editItem(T o) {
    return o;
  }
}
