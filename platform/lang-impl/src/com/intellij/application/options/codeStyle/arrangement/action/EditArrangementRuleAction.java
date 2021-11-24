// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.IconUtil;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 */
public class EditArrangementRuleAction extends AbstractArrangementRuleAction implements DumbAware, Toggleable {
  public EditArrangementRuleAction() {
    getTemplatePresentation().setText(ApplicationBundle.messagePointer("arrangement.action.rule.edit.text"));
    getTemplatePresentation().setDescription(ApplicationBundle.messagePointer("arrangement.action.rule.edit.description"));
    getTemplatePresentation().setIcon(IconUtil.getEditIcon());
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ArrangementMatchingRulesControl control = getRulesControl(e);
    e.getPresentation().setEnabled(control != null && control.getSelectedModelRows().size() == 1);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ArrangementMatchingRulesControl control = getRulesControl(e);
    if (control == null) {
      return;
    }
    IntList rows = control.getSelectedModelRows();
    if (rows.size() != 1) {
      return;
    }
    int row = rows.getInt(0);
    control.showEditor(row);
    scrollRowToVisible(control, row);
  }
}
