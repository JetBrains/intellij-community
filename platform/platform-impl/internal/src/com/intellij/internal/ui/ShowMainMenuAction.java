// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
final class ShowMainMenuAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ActionGroup mainMenu = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU);
    JBPopupFactory.getInstance()
      .createActionGroupPopup("Main Menu", mainMenu,
                              e.getDataContext(),
                              false, true, false, null, 30, null)
      .showInBestPositionFor(e.getDataContext());
  }
}
