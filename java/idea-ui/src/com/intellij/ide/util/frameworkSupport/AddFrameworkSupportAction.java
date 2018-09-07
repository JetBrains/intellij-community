// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.frameworkSupport;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class AddFrameworkSupportAction extends AnAction {
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
  public void update(@NotNull final AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    boolean enable = module != null && AddFrameworkSupportDialog.isAvailable(module);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enable);
    }
    else {
      e.getPresentation().setEnabled(enable);
    }
  }
}
