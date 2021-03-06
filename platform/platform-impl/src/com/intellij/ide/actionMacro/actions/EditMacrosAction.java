// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.ide.actionMacro.ActionMacroConfigurable;
import com.intellij.ide.actionMacro.ActionMacroManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

final class EditMacrosAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ShowSettingsUtil.getInstance().editConfigurable(e.getProject(), "#com.intellij.ide.actionMacro.EditMacrosDialog", new ActionMacroConfigurable());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ActionMacro[] macros = ActionMacroManager.getInstance().getAllMacros();
    e.getPresentation().setEnabled(macros.length > 0);
  }
}
