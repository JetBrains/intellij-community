// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class RevealFileAction extends DumbAwareAction {

  public RevealFileAction() {
    getTemplatePresentation().setText(getActionName());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile file = ShowFilePathAction.findLocalFile(e.getData(CommonDataKeys.VIRTUAL_FILE));
    Presentation presentation = e.getPresentation();
    presentation.setText(getActionName());
    presentation.setEnabled(file != null);
  }

  @NotNull
  public static String getActionName() {
    return SystemInfo.isMac ? "Reveal in Finder" : "Show in " + ShowFilePathAction.getFileManagerName();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile file = ShowFilePathAction.findLocalFile(e.getData(CommonDataKeys.VIRTUAL_FILE));
    if (file != null) {
      ShowFilePathAction.openFile(new File(file.getPresentableUrl()));
    }
  }
}
