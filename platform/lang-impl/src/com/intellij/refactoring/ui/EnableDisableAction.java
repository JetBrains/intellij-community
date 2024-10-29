// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.ui;

import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public abstract class EnableDisableAction extends AbstractAction {
  @Override
  public void actionPerformed(ActionEvent e) {
    if (getTable().isEditing()) return;
    int[] rows = getTable().getSelectedRows();
    if (rows.length > 0) {
      boolean valueToBeSet = false;
      for (final int row : rows) {
        if (!isRowChecked(row)) {
          valueToBeSet = true;
          break;
        }
      }
      applyValue(rows, valueToBeSet);
//          myMyTableModel.fireTableRowsUpdated(rows[0], rows[rows.length - 1]);
    }
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getTable(), true));
  }

  protected abstract JTable getTable();

  protected abstract void applyValue(int[] rows, boolean valueToBeSet);

  protected abstract boolean isRowChecked(int row);

  public void register() {// make SPACE check/uncheck selected rows
    JTable table = getTable();
    @NonNls InputMap inputMap = table.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    final @NonNls ActionMap actionMap = table.getActionMap();
    actionMap.put("enable_disable", this);
  }
}
