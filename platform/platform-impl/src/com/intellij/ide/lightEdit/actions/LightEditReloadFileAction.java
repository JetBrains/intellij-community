// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.actions;

import com.intellij.ide.lightEdit.*;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class LightEditReloadFileAction extends DumbAwareAction implements LightEditCompatible {
  public LightEditReloadFileAction() {
    super(ApplicationBundle.message("light.edit.reload.action"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file != null) {
      LightEditorManagerImpl editorManager = ((LightEditorManagerImpl)LightEditService.getInstance().getEditorManager());
      LightEditorInfo editorInfo = editorManager.findOpen(file);
      if (editorInfo != null) {
        editorManager.reloadFile(file);
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    if (!LightEdit.owns(e.getProject())) {
      presentation.setEnabledAndVisible(false);
    }
    else {
      presentation.setVisible(true);
      VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
      presentation.setEnabled(file != null && file.isInLocalFileSystem());
    }
  }
}
