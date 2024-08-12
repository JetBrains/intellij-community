// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementSectionRulesControl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Svetlana.Zemlyanskaya
 */
public abstract class AbstractArrangementRuleAction extends AnAction {

  protected @Nullable ArrangementMatchingRulesControl getRulesControl(@NotNull AnActionEvent e) {
    return e.getData(ArrangementSectionRulesControl.KEY);
  }

  protected void scrollRowToVisible(@NotNull ArrangementMatchingRulesControl control, int row) {
    control.scrollRowToVisible(row);
  }
}
