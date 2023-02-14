// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

abstract class SameRendererAndEditorColumnInfo<T> extends ColumnInfo<T, T> {

  private final TableCellRenderer myCellRenderer;
  private final TableCellEditor myTableCellEditor;

  SameRendererAndEditorColumnInfo(@NlsContexts.ColumnName String columnName) {
    super(columnName);

    myCellRenderer = (table, value, selected, focused, row, column) -> {
      boolean hovered = TableHoverListener.getHoveredRow(table) == row;
      //noinspection unchecked
      return getCellComponent((TableView<?>)table, (T)value, hovered);
    };

    myTableCellEditor = new AbstractTableCellEditor() {
      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        //noinspection unchecked
        return getCellComponent((TableView<?>)table, (T)value, true);
      }

      @Override
      public Object getCellEditorValue() {
        return null;
      }
    };
  }

  @Override
  public T valueOf(T value) {
    return value;
  }

  @Override
  public boolean isCellEditable(T value) {
    return true;
  }

  @Override
  public TableCellRenderer getRenderer(T value) {
    return myCellRenderer;
  }

  @Override
  public TableCellEditor getEditor(T value) {
    return myTableCellEditor;
  }

  protected abstract @NotNull JComponent getCellComponent(@NotNull TableView<?> table, @NotNull T value, boolean hovered);
}
