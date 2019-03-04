// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.cellvalidators;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;

@ApiStatus.Experimental
public abstract class CellComponentProvider<C extends JComponent> {
  @NotNull protected final C owner;

  public CellComponentProvider(@NotNull C owner) {
    this.owner = owner;
  }

  @NotNull
  public final C getOwner() {
    return owner;
  }

  @Nullable
  abstract public JComponent getCellRendererComponent(@NotNull MouseEvent e);

  @NotNull
  abstract public Rectangle getCellRect(@NotNull MouseEvent e);

  abstract boolean isEditing(@NotNull MouseEvent e);

  abstract boolean isEditingStarted(@NotNull PropertyChangeEvent e);

  public static CellComponentProvider<JTable> forTable(JTable table) {
    return new TableProvider(table);
  }

  /**
   * Convenient classes with standard implementations. Don't use the class directly.
   * It can either be created with {@link CellComponentProvider#forTable(JTable)} method or
   * be extended.
   */
  public static class TableProvider extends CellComponentProvider<JTable> {
    protected TableProvider(@NotNull JTable owner) {
      super(owner);
    }

    @Nullable
    @Override
    public JComponent getCellRendererComponent(@NotNull MouseEvent e) {
      Point p = e.getPoint();
      int column = owner.columnAtPoint(p);
      int row = owner.rowAtPoint(p);

      if ((column != -1) && (row != -1)) {
        TableCellRenderer renderer = owner.getCellRenderer(row, column);
        return (JComponent)owner.prepareRenderer(renderer, row, column);
      } else {
        return null;
      }
    }

    @NotNull
    @Override
    public Rectangle getCellRect(@NotNull MouseEvent e) {
      Point p = e.getPoint();
      return owner.getCellRect(owner.rowAtPoint(p), owner.columnAtPoint(p), false);
    }

    @Override
    public boolean isEditing(@NotNull MouseEvent e) {
      Point p = e.getPoint();
      return owner.rowAtPoint(p) == owner.getEditingRow() && owner.columnAtPoint(p) == owner.getEditingColumn();
    }

    @Override
    public boolean isEditingStarted(@NotNull PropertyChangeEvent e) {
      return "tableCellEditor".equals(e.getPropertyName()) && e.getNewValue() != null;
    }

  }
}