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
package com.intellij.application.options.codeStyle.arrangement.action.tokens;

import com.intellij.application.options.codeStyle.arrangement.action.MoveArrangementMatchingRuleDownAction;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.application.options.codeStyle.arrangement.match.tokens.ArrangementRuleAliasControl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class MoveArrangementAliasRuleDownAction extends MoveArrangementMatchingRuleDownAction {
  @Override
  @Nullable
  protected ArrangementMatchingRulesControl getRulesControl(@NotNull AnActionEvent e) {
    return ArrangementRuleAliasControl.KEY.getData(e.getDataContext());
  }
}
