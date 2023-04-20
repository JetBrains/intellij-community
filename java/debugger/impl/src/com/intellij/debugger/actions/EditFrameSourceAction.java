// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NotNull;

public class EditFrameSourceAction extends GotoFrameSourceAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    AnAction delegate = ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE);
    e.getPresentation().setTextWithMnemonic(delegate.getTemplatePresentation().getTextWithPossibleMnemonic());
  }
}
