// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import org.jetbrains.annotations.NotNull;

public final class ImportProjectAction extends ImportModuleAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ImportModuleAction.doImport(null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(AllIcons.ToolbarDecorator.Import);
    }
    NewProjectAction.updateActionText(this, e);
  }

  @NotNull
  @Override
  public String getActionText(boolean isInNewSubmenu, boolean isInJavaIde) {
    return JavaUiBundle.message("import.project.action.text", isInNewSubmenu ? 1 : 0, isInJavaIde ? 1 : 0);
  }
}
