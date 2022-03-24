// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.IconUtil;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 */
public class RemoveArrangementRuleAction extends AbstractArrangementRuleAction implements DumbAware {
  public RemoveArrangementRuleAction() {
    getTemplatePresentation().setText(ApplicationBundle.messagePointer("arrangement.action.rule.remove.text"));
    getTemplatePresentation().setDescription(ApplicationBundle.messagePointer("arrangement.action.rule.remove.description"));
    getTemplatePresentation().setIcon(IconUtil.getRemoveIcon());
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ArrangementMatchingRulesControl control = getRulesControl(e);
    e.getPresentation().setEnabled(control != null && !control.getSelectedModelRows().isEmpty() && control.getEditingRow() == -1);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ArrangementMatchingRulesControl control = getRulesControl(e);
    if (control == null) {
      return;
    }
    
    control.hideEditor();

    final IntList rowsToRemove = control.getSelectedModelRows();
    if (rowsToRemove.isEmpty()) {
      return;
    }

    final ArrangementMatchingRulesModel model = control.getModel();
    control.runOperationIgnoreSelectionChange(() -> {
      for (int i = 0; i < rowsToRemove.size(); i++) {
        int row = rowsToRemove.getInt(i);
        model.removeRow(row);
      }
    });
  }
}
