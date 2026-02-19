// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class MoveModulesOutsideGroupAction extends AnAction {

  public MoveModulesOutsideGroupAction() {
    super(IdeBundle.messagePointer("action.move.module.outside.any.group"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Module[] modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
    if (modules == null || modules.length == 0) return;
    MoveModulesToGroupAction.doMove(modules, null, e.getDataContext());
  }
}