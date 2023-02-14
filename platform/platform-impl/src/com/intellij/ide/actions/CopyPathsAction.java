// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

import static com.intellij.openapi.actionSystem.ActionPlaces.KEYBOARD_SHORTCUT;

public class CopyPathsAction extends AnAction implements DumbAware {
  public CopyPathsAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null && files.length > 0) {
      CopyPasteManager.getInstance().setContents(new StringSelection(getPaths(files)));
    }
  }

  private static String getPaths(VirtualFile[] files) {
    StringBuilder buf = new StringBuilder(files.length * 64);
    for (VirtualFile file : files) {
      if (buf.length() > 0) buf.append('\n');
      buf.append(file.getPresentableUrl());
    }
    return buf.toString();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile[] files;
    final boolean enabled =
      KEYBOARD_SHORTCUT.equals(e.getPlace()) &&
      ((files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)) != null) &&
      (files.length > 0);

    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
