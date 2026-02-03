// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public final class ArrangementRemoveConditionAction extends AnAction {

  public ArrangementRemoveConditionAction() {
    getTemplatePresentation().setIcon(AllIcons.Actions.Close);
    getTemplatePresentation().setHoveredIcon(AllIcons.Actions.CloseHovered);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
  }
}
