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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.Consumer;
import com.intellij.util.Function;

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
    Function<Boolean,Boolean> function = ArrangementConstants.UPDATE_MOVE_RULE_FUNCTION_KEY.getData(e.getDataContext());
    e.getPresentation().setEnabled(function != null && function.fun(true));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Consumer<Boolean> function = ArrangementConstants.MOVE_RULE_FUNCTION_KEY.getData(e.getDataContext());
    if (function != null) {
      function.consume(true);
    }
  }
}
