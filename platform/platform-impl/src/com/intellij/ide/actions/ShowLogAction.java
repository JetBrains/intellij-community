// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author pegov
 */
public class ShowLogAction extends AnAction implements DumbAware {
  public ShowLogAction() {
    getTemplatePresentation().setText(getActionName());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final File logFile = new File(PathManager.getLogPath(), "idea.log");
    RevealFileAction.openFile(logFile);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setVisible(RevealFileAction.isSupported());
    presentation.setText(getActionName());
  }

  @NotNull
  public static String getActionName() {
    return "Show Log in " + RevealFileAction.getFileManagerName();
  }
}
