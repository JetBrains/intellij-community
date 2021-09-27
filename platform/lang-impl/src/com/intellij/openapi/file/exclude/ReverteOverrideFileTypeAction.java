// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/** removes destruction caused by {@link com.intellij.openapi.file.exclude.OverrideFileTypeAction} and restore the original file type */
class ReverteOverrideFileTypeAction extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean overridden = file != null && !file.isDirectory() && OverrideFileTypeManager.getInstance().getFileValue(file) != null;
    e.getPresentation().setEnabledAndVisible(overridden);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null) return;
    OverrideFileTypeManager.getInstance().removeFile(file);
  }
}
