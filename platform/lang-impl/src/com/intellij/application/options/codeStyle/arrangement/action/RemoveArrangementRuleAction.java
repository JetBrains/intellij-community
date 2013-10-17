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

import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.SystemInfoRt;
import gnu.trove.TIntArrayList;

/**
 * @author Denis Zhdanov
 * @since 8/26/12 7:41 PM
 */
public class RemoveArrangementRuleAction extends AnAction {

  public RemoveArrangementRuleAction() {
    getTemplatePresentation().setText(ApplicationBundle.message("arrangement.action.rule.remove.text"));
    getTemplatePresentation().setDescription(ApplicationBundle.message("arrangement.action.rule.remove.description"));
  }

  @Override
  public void update(AnActionEvent e) {
   ArrangementMatchingRulesControl control = ArrangementMatchingRulesControl.KEY.getData(e.getDataContext());
    e.getPresentation().setEnabled(control != null && !control.getSelectedModelRows().isEmpty());
    e.getPresentation().setIcon(SystemInfoRt.isMac ? AllIcons.ToolbarDecorator.Mac.Remove : AllIcons.ToolbarDecorator.Remove);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ArrangementMatchingRulesControl control = ArrangementMatchingRulesControl.KEY.getData(e.getDataContext());
    if (control == null) {
      return;
    }
    
    control.hideEditor();

    final TIntArrayList rowsToRemove = control.getSelectedModelRows();
    if (rowsToRemove.isEmpty()) {
      return;
    }

    final ArrangementMatchingRulesModel model = control.getModel();
    control.runOperationIgnoreSelectionChange(new Runnable() {
      @Override
      public void run() {
        for (int i = 0; i < rowsToRemove.size(); i++) {
          int row = rowsToRemove.get(i);
          model.removeRow(row);
        } 
      }
    });
  }
}
