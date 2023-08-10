// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public final class LightEditNewFileAction extends DumbAwareAction implements LightEditCompatible {
  public LightEditNewFileAction() {
    super(ActionsBundle.messagePointer("action.LightEditNewFileAction.text"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    LightEditService.getInstance().createNewDocument(null);
  }
}
