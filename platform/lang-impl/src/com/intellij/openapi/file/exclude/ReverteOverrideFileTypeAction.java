// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/** removes destruction caused by {@link com.intellij.openapi.file.exclude.OverrideFileTypeAction} and restore the original file type */
class ReverteOverrideFileTypeAction extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile[] files = OverrideFileTypeAction.getContextFiles(e, file -> OverrideFileTypeManager.getInstance().getFileValue(file) != null);
    Presentation presentation = e.getPresentation();
    boolean enabled = files.length != 0;
    presentation.setDescription(enabled
                                ? ActionsBundle.message("action.ReverteOverrideFileTypeAction.verbose.description", files[0].getName(), files.length - 1)
                                : ActionsBundle.message("action.ReverteOverrideFileTypeAction.description"));
    presentation.setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile[] files = OverrideFileTypeAction.getContextFiles(e, file -> OverrideFileTypeManager.getInstance().getFileValue(file) != null);
    for (VirtualFile file : files) {
      OverrideFileTypeManager.getInstance().removeFile(file);
    }
  }
}
