// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  protected final @NotNull C owner;

  public CellComponentProvider(@NotNull C owner) {
    this.owner = owner;
  }

  public final @NotNull C getOwner() {
    return owner;
  }

  public abstract @Nullable JComponent getCellRendererComponent(@NotNull MouseEvent e);

  public abstract @NotNull Rectangle getCellRect(@NotNull MouseEvent e);

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

    @Override
    public @Nullable JComponent getCellRendererComponent(@NotNull MouseEvent e) {
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

    @Override
    public @NotNull Rectangle getCellRect(@NotNull MouseEvent e) {
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