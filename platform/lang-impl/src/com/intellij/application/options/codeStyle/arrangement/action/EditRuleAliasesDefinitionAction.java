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
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.application.options.codeStyle.arrangement.match.ArrangementSectionRulesControl;
import com.intellij.application.options.codeStyle.arrangement.match.tokens.ArrangementRuleAliasDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class EditRuleAliasesDefinitionAction extends AnAction {

  public EditRuleAliasesDefinitionAction() {
    getTemplatePresentation().setText(ApplicationBundle.message("arrangement.action.alias.rule.add.edit.text"));
    getTemplatePresentation().setDescription(ApplicationBundle.message("arrangement.action.alias.rule.add.edit.description"));
    getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
  }

  @Override
  public void update(AnActionEvent e) {
    final ArrangementSectionRulesControl control = ArrangementSectionRulesControl.KEY.getData(e.getDataContext());
    if (control == null) {
      return;
    }
    e.getPresentation().setEnabledAndVisible(control.getRulesAliases() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ArrangementSectionRulesControl control = ArrangementSectionRulesControl.KEY.getData(e.getDataContext());
    if (control == null || control.getRulesAliases() == null) {
      return;
    }

    control.hideEditor();
    final ArrangementRuleAliasDialog dialog = control.createRuleAliasEditDialog();
    if (dialog.showAndGet() && dialog.isModified()) {
      control.setRulesAliases(dialog.getRuleAliases());
    }
  }
}
