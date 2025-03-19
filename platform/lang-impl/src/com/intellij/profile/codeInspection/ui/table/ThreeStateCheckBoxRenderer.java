// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui.table;

import com.intellij.ui.render.RenderingUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ThreeStateCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.EventObject;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class ThreeStateCheckBoxRenderer extends ThreeStateCheckBox implements TableCellRenderer, TableCellEditor {

  private final List<CellEditorListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public ThreeStateCheckBoxRenderer() {
    setThirdStateEnabled(false);
    setHorizontalAlignment(CENTER);
    setVerticalAlignment(CENTER);
    addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        stopCellEditing();
      }
    });
  }

  @Override
  public Component getTableCellEditorComponent(final JTable table, final Object value, final boolean isSelected, final int row, final int column) {
    JCheckBox checkBox = tune(value, isSelected, row, table, false);
    checkBox.setOpaque(true);
    return checkBox;
  }

  @Override
  public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
    return tune(value, isSelected, row, table, hasFocus);
  }

  private JCheckBox tune(final Object value, final boolean isSelected, final int row, final JTable table, boolean hasFocus) {
    setForeground(RenderingUtil.getForeground(table, isSelected));
    setBackground(RenderingUtil.getBackground(table, isSelected));

    if (value == null) {
      setState(State.DONT_CARE);
    } else {
      setSelected((Boolean) value);
    }

    return this;
  }

  @Override
  public @Nullable Object getCellEditorValue() {
    return getState() != State.DONT_CARE ? isSelected() : null;
  }

  @Override
  public boolean isCellEditable(final EventObject anEvent) {
    return true;
  }

  @Override
  public boolean shouldSelectCell(final EventObject anEvent) {
    return true;
  }

  @Override
  public boolean stopCellEditing() {
    final ChangeEvent e = new ChangeEvent(this);
    for (final CellEditorListener listener : myListeners) {
      listener.editingStopped(e);
    }
    return true;
  }

  @Override
  public void cancelCellEditing() {
    final ChangeEvent e = new ChangeEvent(this);
    for (final CellEditorListener listener : myListeners) {
      listener.editingCanceled(e);
    }
  }

  @Override
  public void addCellEditorListener(final CellEditorListener l) {
    myListeners.add(l);
  }

  @Override
  public void removeCellEditorListener(final CellEditorListener l) {
    myListeners.remove(l);
  }
}
