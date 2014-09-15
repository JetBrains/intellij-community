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

import com.intellij.application.options.codeStyle.arrangement.group.ArrangementGroupingRulesControl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.IconUtil;

import javax.swing.table.DefaultTableModel;

/**
 * @author Denis Zhdanov
 * @since 11/14/12 10:47 AM
 */
public class MoveArrangementGroupingRuleDownAction extends AnAction implements DumbAware {

  public MoveArrangementGroupingRuleDownAction() {
    getTemplatePresentation().setText(ApplicationBundle.message("arrangement.action.rule.move.down.text"));
    getTemplatePresentation().setDescription(ApplicationBundle.message("arrangement.action.rule.move.down.description"));
    getTemplatePresentation().setIcon(IconUtil.getMoveDownIcon());
  }

  @Override
  public void update(AnActionEvent e) {
    ArrangementGroupingRulesControl control = ArrangementGroupingRulesControl.KEY.getData(e.getDataContext());
    if (control == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    int[] rows = control.getSelectedRows();
    e.getPresentation().setEnabled(rows.length == 1 && rows[0] != control.getRowCount() - 1);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ArrangementGroupingRulesControl control = ArrangementGroupingRulesControl.KEY.getData(e.getDataContext());
    if (control == null) {
      return;
    }

    int[] rows = control.getSelectedRows();
    int row = rows[0];
    if (rows.length != 1 || rows[0] == control.getRowCount() - 1) {
      return;
    }

    if (control.isEditing()) {
      control.getCellEditor().stopCellEditing();
    }

    DefaultTableModel model = control.getModel();
    Object value = model.getValueAt(row, 0);
    model.removeRow(row);
    model.insertRow(row + 1, new Object[] { value });
    control.getSelectionModel().setSelectionInterval(row + 1, row + 1);
  }
}
