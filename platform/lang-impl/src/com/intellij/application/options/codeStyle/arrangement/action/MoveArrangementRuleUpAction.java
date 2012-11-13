/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesModel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAware;
import gnu.trove.TIntArrayList;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 9/28/12 12:16 PM
 */
public class MoveArrangementRuleUpAction extends AnAction implements DumbAware {

  public MoveArrangementRuleUpAction() {
    getTemplatePresentation().setText(ApplicationBundle.message("arrangement.action.rule.move.up.text"));
    getTemplatePresentation().setDescription(ApplicationBundle.message("arrangement.action.rule.move.up.description"));
  }

  @Override
  public void update(AnActionEvent e) {
    ArrangementMatchingRulesControl control = ArrangementConstants.MATCHING_RULES_CONTROL_KEY.getData(e.getDataContext());
    if (control == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    TIntArrayList rows = control.getSelectedModelRows();
    rows.reverse();
    int top = -1;
    for (int i = 0; i < rows.size(); i++) {
      int row = rows.get(i);
      if (row == top + 1) {
        top++;
      }
      else {
        e.getPresentation().setEnabled(true);
        return;
      }
    }
    e.getPresentation().setEnabled(false);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ArrangementMatchingRulesControl control = ArrangementConstants.MATCHING_RULES_CONTROL_KEY.getData(e.getDataContext());
    if (control == null) {
      return;
    }

    final int editing = control.getEditingRow() - 1;

    control.runOperationIgnoreSelectionChange(new Runnable() {
      @Override
      public void run() {
        control.hideEditor();
        final List<int[]> mappings = new ArrayList<int[]>();
        TIntArrayList rows = control.getSelectedModelRows();
        rows.reverse();
        int top = -1;
        for (int i = 0; i < rows.size(); i++) {
          int row = rows.get(i);
          if (row == top + 1) {
            mappings.add(new int[] { row, row });
            top++;
          }
          else {
            mappings.add(new int[]{ row, row - 1 });
          }
        }

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
        

        if (newRowToEdit >= 0) {
          control.showEditor(newRowToEdit);
        }
      }
    });
    control.repaintRows(0, control.getModel().getSize() - 1, true);
  }
}
