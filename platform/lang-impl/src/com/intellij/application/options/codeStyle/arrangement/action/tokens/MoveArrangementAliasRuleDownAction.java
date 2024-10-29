// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public final class MoveArrangementAliasRuleDownAction extends MoveArrangementMatchingRuleDownAction {
  @Override
  protected @Nullable ArrangementMatchingRulesControl getRulesControl(@NotNull AnActionEvent e) {
    return e.getData(ArrangementRuleAliasControl.KEY);
  }
}
