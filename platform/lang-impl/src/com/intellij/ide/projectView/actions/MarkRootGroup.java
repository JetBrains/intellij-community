// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions;

import com.intellij.ide.actions.NonTrivialActionGroup;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class MarkRootGroup extends NonTrivialActionGroup {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (e.getPresentation().isVisible()) {
      String textKey = isFilesOnlySelection(e) ? "group.MarkRootGroup.file.text" : "group.MarkRootGroup.text";
      e.getPresentation().setText(ActionsBundle.message(textKey));
    }
  }

  @VisibleForTesting
  public static boolean isFilesOnlySelection(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files == null || files.length == 0) return false;
    for (VirtualFile file : files) {
      if (file.isDirectory()) return false;
    }
    return true;
  }
}
