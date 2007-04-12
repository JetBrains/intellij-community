/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;


public class SynchronizeCurrentFileAction extends AnAction {

  public void update(AnActionEvent e) {
    final VirtualFile[] files = e.getData(DataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null && files.length > 0) {
      String message = getMessage(files);
      e.getPresentation().setEnabled(true);
      e.getPresentation().setText(message);
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  private static String getMessage(final VirtualFile[] files) {
    String message;
    if (files.length == 1) {
      message = IdeBundle.message("action.synchronize.file", files[0].getName());
    }
    else {
      message = IdeBundle.message("action.synchronize.selected.files");
    }
    return message;
  }

  public void actionPerformed(final AnActionEvent e) {
    final VirtualFile[] files = e.getData(DataKeys.VIRTUAL_FILE_ARRAY);

    final Project project = e.getData(DataKeys.PROJECT);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final VirtualFile file : files) {
          file.refresh(false, true);
        }
      }
    });

    final FileStatusManager statusManager = FileStatusManager.getInstance(project);
    for (VirtualFile file : files) {
      statusManager.fileStatusChanged(file);
    }
    String message = IdeBundle.message("action.sync.completed.successfully", getMessage(files));
    WindowManager.getInstance().getStatusBar(project).setInfo(message);
  }
}