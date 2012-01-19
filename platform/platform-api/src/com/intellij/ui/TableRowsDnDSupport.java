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
import com.intellij.util.ui.EditableModel;
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

  public static void install(@NotNull final JTable table, @NotNull final EditableModel model) {
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
          return new DnDDragStartBean(new TableRowDragInfo(table, Integer.valueOf(table.rowAtPoint(p))));
        }
      })
      .setTargetChecker(new DnDTargetChecker() {
        @Override
        public boolean update(DnDEvent event) {
          final Object o = event.getAttachedObject();
          event.setDropPossible(o instanceof TableRowDragInfo && ((TableRowDragInfo)o).table == table);
          return false;
        }
      })
      .setDropHandler(new DnDDropHandler() {
        @Override
        public void drop(DnDEvent event) {
          final Object o = event.getAttachedObject();
          final Point p = event.getPoint();
          if (o instanceof TableRowDragInfo && ((TableRowDragInfo)o).table == table) {
            int oldIndex = ((TableRowDragInfo)o).row;
            if (oldIndex == -1) return;
            int newIndex = table.rowAtPoint(p);
            if (newIndex == -1) {
              newIndex = table.getRowCount() - 1;
            }
            int min = Math.min(oldIndex, newIndex);
            int max = Math.max(oldIndex, newIndex);
            if (newIndex > oldIndex) {
              while (min < max) {
                model.exchangeRows(min, min + 1);
                min++;
              }
              table.getSelectionModel().setSelectionInterval(min, min);
            }
            else {
              while (max > min) {
                model.exchangeRows(max, max - 1);
                max--;
              }
              table.getSelectionModel().setSelectionInterval(max, max);
            }
          }
        }
      }).install();
  }
  
  static class TableRowDragInfo {
    public final JTable table;
    public final int row;

    TableRowDragInfo(JTable table, int row) {
      this.table = table;
      this.row = row;
    }
  }
}
