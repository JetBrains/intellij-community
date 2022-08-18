// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesModel;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Zhdanov
 */
public abstract class AbstractMoveArrangementRuleAction extends AbstractArrangementRuleAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    ArrangementMatchingRulesControl control = getRulesControl(e);
    if (control == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    final List<int[]> mappings = new ArrayList<>();
    fillMappings(control, mappings);
    for (int[] mapping : mappings) {
      if (mapping[0] != mapping[1]) {
        e.getPresentation().setEnabled(true);
        return;
      }
    }
    e.getPresentation().setEnabled(false);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ArrangementMatchingRulesControl control = getRulesControl(e);
    if (control == null) {
      return;
    }

    final int editing = control.getEditingRow() - 1;

    control.runOperationIgnoreSelectionChange(() -> {
      control.hideEditor();
      final List<int[]> mappings = new ArrayList<>();
      fillMappings(control, mappings);

      if (mappings.isEmpty()) {
        return;
      }

      int newRowToEdit = editing;
      ArrangementMatchingRulesModel model = control.getModel();
      Object value;
      int from;
      int to;
      for (int[] pair : mappings) {
        from = pair[0];
        to = pair[1];
        if (from != to) {
          value = model.getElementAt(from);
          model.removeRow(from);
          model.insert(to, value);
          if (newRowToEdit == from) {
            newRowToEdit = to;
          }
        }
      }

      ListSelectionModel selectionModel = control.getSelectionModel();
      for (int[] pair : mappings) {
        selectionModel.addSelectionInterval(pair[1], pair[1]);
      }


      int visibleRow = -1;
      if (newRowToEdit >= 0) {
        control.showEditor(newRowToEdit);
        visibleRow = newRowToEdit;
      }
      else if (!mappings.isEmpty()) {
        visibleRow = mappings.get(0)[1];
      }

      if (visibleRow != -1) {
        scrollRowToVisible(control, visibleRow);
      }
    });
    control.repaintRows(0, control.getModel().getSize() - 1, true);

  }

  protected abstract void fillMappings(@NotNull ArrangementMatchingRulesControl control, @NotNull List<int[]> mappings);
}
