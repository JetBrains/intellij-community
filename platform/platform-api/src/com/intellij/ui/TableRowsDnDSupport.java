/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.ide.dnd.*;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class TableRowsDnDSupport {
  private TableRowsDnDSupport() {
  }

  public static void install(@NotNull final JTable table, @NotNull final RowEditableTableModel model) {
    table.setDragEnabled(true);
    //table.setDropMode(DropMode.ON);
    DnDSupport.createBuilder(table)
      .setBeanProvider(new Function<DnDActionInfo, DnDDragStartBean>() {
        @Override
        public DnDDragStartBean fun(DnDActionInfo info) {
          final Point p = info.getPoint();
          final TableCellEditor cellEditor = table.getCellEditor();
          if (cellEditor != null) {
            cellEditor.stopCellEditing();
          }
          return new DnDDragStartBean(Integer.valueOf(table.rowAtPoint(p)));
        }
      })
      .setDropHandler(new DnDDropHandler() {
        @Override
        public void drop(DnDEvent event) {
          final Object o = event.getAttachedObject();
          final Point p = event.getPoint();
          if (o instanceof Integer) {
            final int oldIndex = ((Integer)o).intValue();
            final int newIndex = table.rowAtPoint(p);
            model.exchangeRows(oldIndex, newIndex);
          }
        }
      }).install();
  }
}
