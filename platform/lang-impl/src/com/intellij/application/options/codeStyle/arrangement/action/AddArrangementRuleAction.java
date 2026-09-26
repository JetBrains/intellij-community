// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesModel;
import com.intellij.application.options.codeStyle.arrangement.match.EmptyArrangementRuleComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.IconUtil;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

public class AddArrangementRuleAction extends AbstractArrangementRuleAction implements DumbAware {
  public AddArrangementRuleAction() {
    getTemplatePresentation().setText(ApplicationBundle.messagePointer("arrangement.action.rule.add.text"));
    getTemplatePresentation().setDescription(ApplicationBundle.messagePointer("arrangement.action.rule.add.description"));
    getTemplatePresentation().setIcon(IconUtil.getAddIcon());
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ArrangementMatchingRulesControl control = getRulesControl(e);
    e.getPresentation().setEnabled(control != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ArrangementMatchingRulesControl control = getRulesControl(e);
    if (control == null) {
      return;
    }
    
    control.hideEditor();
    IntList rows = control.getSelectedModelRows();
    ArrangementMatchingRulesModel model = control.getModel();
    int rowToEdit;
    if (rows.size() == 1) {
      rowToEdit = rows.getInt(0) + 1;
      model.insertRow(rowToEdit, new Object[] {createNewRule(control)});
    }
    else {
      rowToEdit = model.getSize();
      model.add(createNewRule(control));
    }
    showEditor(control, rowToEdit);
    control.getSelectionModel().setSelectionInterval(rowToEdit, rowToEdit);
    scrollRowToVisible(control, rowToEdit);
  }

  protected @NotNull Object createNewRule(@NotNull ArrangementMatchingRulesControl control) {
    return new EmptyArrangementRuleComponent(control.getEmptyRowHeight());
  }

  protected void showEditor(@NotNull ArrangementMatchingRulesControl control, int rowToEdit) {
    control.showEditor(rowToEdit);
  }
}
