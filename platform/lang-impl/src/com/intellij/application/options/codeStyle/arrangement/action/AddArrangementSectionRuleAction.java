// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementSectionRuleManager;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementSectionRulesControl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public final class AddArrangementSectionRuleAction extends AddArrangementRuleAction {

  public AddArrangementSectionRuleAction() {
    getTemplatePresentation().setText(ApplicationBundle.messagePointer("arrangement.action.section.rule.add.text"));
    getTemplatePresentation().setDescription(ApplicationBundle.messagePointer("arrangement.action.section.rule.add.description"));
    getTemplatePresentation().setIcon(AllIcons.CodeStyle.AddNewSectionRule);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final ArrangementMatchingRulesControl control = getRulesControl(e);
    if (!(control instanceof ArrangementSectionRulesControl)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(((ArrangementSectionRulesControl)control).getSectionRuleManager() != null);
  }

  @Override
  protected @NotNull Object createNewRule(@NotNull ArrangementMatchingRulesControl control) {
    final ArrangementSectionRuleManager manager = ((ArrangementSectionRulesControl)control).getSectionRuleManager();
    assert manager != null;
    return manager.createDefaultSectionRule();
  }

  @Override
  protected void showEditor(@NotNull ArrangementMatchingRulesControl control, int rowToEdit) {
    final ArrangementSectionRuleManager manager = ((ArrangementSectionRulesControl)control).getSectionRuleManager();
    if (manager != null) {
      manager.showEditor(rowToEdit);
    }
  }
}
