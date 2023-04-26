// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.frameworkSupport;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

final class AddFrameworkSupportAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    if (module == null) return;

    AddFrameworkSupportDialog dialog = AddFrameworkSupportDialog.createDialog(module);
    if (dialog != null) {
      dialog.show();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(false);
    }
    else {
      Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
      boolean enable = module != null && AddFrameworkSupportDialog.isAvailable(module);
      e.getPresentation().setEnabledAndVisible(enable);
    }
  }
}