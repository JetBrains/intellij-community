/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui.table;

import com.intellij.ui.ClickListener;
import com.intellij.util.SmartList;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class ThreeStateCheckBoxRenderer extends ThreeStateCheckBox implements TableCellRenderer, TableCellEditor {

  private final List<CellEditorListener> myListeners = new SmartList<CellEditorListener>();

  public ThreeStateCheckBoxRenderer() {
    setThirdStateEnabled(false);
    setHorizontalAlignment(CENTER);
    setVerticalAlignment(CENTER);
  }

  @Override
  public Component getTableCellEditorComponent(final JTable table, final Object value, final boolean isSelected, final int row, final int column) {
    return tune(value, isSelected, row, table);
  }

  @Override
  public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
    return tune(value, isSelected, row, table);
  }

  private JCheckBox tune(final Object value, final boolean isSelected, final int row, final JTable table) {
    final Color bg = UIUtil.isUnderNimbusLookAndFeel() && row % 2 == 1 ? UIUtil.TRANSPARENT_COLOR : table.getBackground();
    final Color fg = table.getForeground();
    final Color selBg = table.getSelectionBackground();
    final Color selFg = table.getSelectionForeground();

    setForeground(isSelected ? selFg : fg);
    setBackground(isSelected ? selBg : bg);

    if (value == null) {
      setState(State.DONT_CARE);
    } else {
      setSelected((Boolean) value);
    }
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull final MouseEvent event, final int clickCount) {
        if (clickCount == 1) {
          stopCellEditing();
          return true;
        }
        return false;
      }
    }.installOn(this);
    return this;
  }

  @Nullable
  @Override
  public Object getCellEditorValue() {
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
