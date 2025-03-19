// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.frameworkSupport;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

final class AddFrameworkSupportAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    if (module == null) return;

    AddFrameworkSupportDialog dialog = AddFrameworkSupportDialog.createDialog(module);
    if (dialog != null) {
      dialog.show();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (e.isFromContextMenu()) {
      e.getPresentation().setVisible(false);
    }
    else {
      Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
      boolean enable = module != null && AddFrameworkSupportDialog.isAvailable(module);
      e.getPresentation().setEnabledAndVisible(enable);
    }
  }
}