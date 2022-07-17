// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.ide.fileTemplates.ui.ConfigureTemplatesDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

public class EditFileTemplatesAction extends DumbAwareAction {
  public EditFileTemplatesAction(@NlsActions.ActionText String text) {
    super(text);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e){
    ConfigureTemplatesDialog dialog = new ConfigureTemplatesDialog(e.getProject());
    dialog.show();
  }
}
